import components.para
import dev.fritz2.core.RenderContext
import dev.fritz2.core.render
import dev.fritz2.routing.MapRouter
import metrics.metrics
import ratedsearches.ratedSearches
import search.ActiveSearchPluginConfiguration
import search.searchScreen
import searchpluginconfig.pluginConfiguration

suspend fun main() {
    koinInit()
    render("#target") { // using id selector here, leave blank to use document.body by default
        div("h-screen flex flex-col overflow-hidden") {
            div("bg-blueBright-50 p-1.5 flex flex-row gap-x-3 w-full align-middle") {
                rankQuestStudio()
                menu()
            }
            div("bg-white overflow-auto grow-0 h-full w-full") {
                mainView()
            }
        }
    }
}


private fun RenderContext.rankQuestStudio() {
    h1("text-blueBright-700 font-bold m-0") { +"Rankquest Studio" }
}

private fun RenderContext.mainView() {
    val router by koin.inject<MapRouter>()
    val activeSearchPluginConfiguration by koin.inject<ActiveSearchPluginConfiguration>()

    div {
        router.select(key = "page").render { (selected, _) ->
//            console.log("selected $selected")
            when (Page.resolve(selected)) {
                Page.Search -> div {
                    searchScreen()
                }
                Page.Conf -> {
                    pluginConfiguration()
                }
                Page.RatedSearches -> ratedSearches()
                Page.Metrics -> metrics()
                Page.Root -> {
                    if(activeSearchPluginConfiguration.current == null) {
                        router.navTo(Page.Conf.route)
                    } else {
                        router.navTo(Page.Search.route)
                    }
                }

            }
        }
    }
}


/**

manage search APIs

Search plugin configurations:

- templated json POST
- extract template variables from json string
- add headers
- json path for items, id, and label
- export configuration

- search explore UI
- populate query context
- review results
- add to test cases

- test case browser
- list test cases
- open test case
- edit query context
- result rater (star rating)
- benchmark configuration
- sane defaults
- add/remove metric configuration
- configure metric configuration
- run benchmark
- review results
- detail for each test case
- export/import
- diff
- export/import

- cli run benchmark
- cli search
- cli add test case
 */