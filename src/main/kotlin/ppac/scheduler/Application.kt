package ppac.scheduler

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod.GET
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.scheduling.support.CronTrigger
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import ppac.scheduler.DynamoConfigProperties.ActionType.MSG
import ppac.scheduler.DynamoConfigProperties.ActionType.URL
import javax.annotation.PostConstruct

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}

@SpringBootApplication
@EnableConfigurationProperties(DynamoConfigProperties::class)
class Application {
    @Bean
    fun taskScheduler(): TaskScheduler = ThreadPoolTaskScheduler().apply { poolSize = 20 }

    @Bean
    fun restTemplate(): RestTemplate = RestTemplate()
}

@Component
class SimpleScheduler(
        private val scheduler: TaskScheduler,
        private val config: DynamoConfigProperties,
        private val rest: RestTemplate
) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @PostConstruct
    fun init() {
        logger.info("Config: $config")

        config.actions.forEach {
            when (it.type) {
                MSG -> scheduler.schedule(Runnable { logger.info(it.value) }, CronTrigger(it.cron))
                URL -> scheduler.schedule(Runnable { doGET(it.value) }, CronTrigger(it.cron))
            }
        }
    }

    private fun doGET(url: String) {
        val headers = HttpHeaders().apply { config.urlHeaders.forEach(::add) }
        rest.exchange(url, GET, HttpEntity<Unit>(headers), String::class.java)
    }
}

@ConfigurationProperties("scheduler")
@ConstructorBinding
data class DynamoConfigProperties(
        val urlHeaders: Map<String, String>,
        val actions: List<Action>
) {
    data class Action(val type: ActionType, val cron: String, val value: String)
    enum class ActionType { MSG, URL }
}
