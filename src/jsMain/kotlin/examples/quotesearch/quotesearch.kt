package examples.quotesearch

import com.jilesvangurp.rankquest.core.DEFAULT_JSON
import com.jilesvangurp.rankquest.core.SearchPlugin
import com.jilesvangurp.rankquest.core.SearchResults
import com.jilesvangurp.rankquest.core.pluginconfiguration.Metric
import com.jilesvangurp.rankquest.core.pluginconfiguration.MetricConfiguration
import com.jilesvangurp.rankquest.core.pluginconfiguration.SearchContextField
import com.jilesvangurp.rankquest.core.pluginconfiguration.SearchPluginConfiguration
import com.jilesvangurp.rankquest.core.plugins.PluginFactory
import com.jilesvangurp.rankquest.core.plugins.PluginFactoryRegistry
import dev.fritz2.core.RootStore
import dev.fritz2.remote.http
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import search.*
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds


val quoteSearchModule = module {
    singleOf(::MovieQuotesStore)

    single {
        val movieQuotesStoreFactory= MovieQuotesStorePluginFactory(get())

        PluginFactoryRegistry().also {
            it.register("movies", movieQuotesStoreFactory)
        }
    }

}

val movieQuotesSearchPluginConfig = SearchPluginConfiguration(
    title = "Movie Quote Search",
    pluginType = "movies",
    fieldConfig = listOf(
        SearchContextField.StringField("q"),
        SearchContextField.IntField("size", 5)
    ),
    pluginSettings = null,
    metrics = Metric.entries.map { MetricConfiguration(it.name, it, listOf()) }
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class MovieQuote(
    // original data has no id, we overwrite this with a simple for loop
    @EncodeDefault val id: String = "-",
    val quote: String,
    val movie: String,
    val type: String,
    val year: Int
)

fun List<MovieQuote>.searchPlugin(): SearchPlugin {
    val documentIndex = DocumentIndex(
        mutableMapOf(
            "quote" to TextFieldIndex(),
            "movie" to TextFieldIndex()
        )
    )
    val labels = mutableMapOf<String, String>()
    this.indices.forEach {
        val q = this[it]
        labels[q.id] = "${q.movie}, ${q.year}: ${q.quote}"
        documentIndex.index(
            Document(
                q.id, mapOf(
                    "quote" to listOf(q.quote),
                    "movie" to listOf(q.movie),
                )
            )
        )
    }

    return object : SearchPlugin {
        override suspend fun fetch(
            searchContext: Map<String, String>,
            numberOfItemsToFetch: Int
        ): Result<SearchResults> {
            val text = searchContext["q"] ?: ""
            val hits = documentIndex.search {
                query = if (text.isNotBlank()) {
                    BoolQuery(
                        should = listOf(
                            MatchQuery("quote", text, prefixMatch = true),
                            MatchQuery("movie", text, prefixMatch = true, boost = 0.25),

                            )
                    )
                } else {
                    MatchAll()
                }
            }.let {
                it.subList(0, min(numberOfItemsToFetch, it.size))
            }
            return SearchResults(hits.size.toLong(), 0.milliseconds, hits.map { (id, score) ->
                SearchResults.SearchResult(id, labels[id] + " ($score)")
            }).let { Result.success(it) }
        }
    }
}

class MovieQuotesStore : RootStore<List<MovieQuote>>(listOf()) {
    val load = handle<String> { _, path ->
        http(path).get().body().let<String, List<MovieQuote>> { body ->
            DEFAULT_JSON.decodeFromString(body)
        }.let { quotes ->
            // set the id property
            quotes.indices.map { i -> quotes[i].copy(id = "$i") }
        }
    }
    init {
        load("moviequotes.json")
        console.log("done loading")
    }
}

class MovieQuotesStorePluginFactory(val movieQuotesStore: MovieQuotesStore) : PluginFactory {
    override fun create(configuration: SearchPluginConfiguration): SearchPlugin {
        return movieQuotesStore.current.searchPlugin()
    }
}


