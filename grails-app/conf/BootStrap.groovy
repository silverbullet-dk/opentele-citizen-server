import grails.converters.JSON
import  opentele.server.citizen.ConferenceCallJob
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormatter
import org.joda.time.format.ISODateTimeFormat
import org.opentele.server.core.util.CustomDomainClassJSONMarshaller
import org.opentele.server.core.util.CustomGroovyBeanJSONMarshaller
import org.opentele.server.core.util.JSONMarshallerUtil
import org.springframework.web.context.support.WebApplicationContextUtils

@SuppressWarnings("GroovyDocCheck")
class BootStrap {

    def springSecurityService
    def grailsApplication

    def init = { servletContext ->

        println "Initializing OpenTele client server"

        def applicationContext = grailsApplication.mainContext
        configureDatasource(applicationContext)

        // Setup marshaller
        JSONMarshallerUtil.registerCustomJSONMarshallers(grailsApplication)

        if (Boolean.valueOf(grailsApplication.config.video.enabled)) {
            ConferenceCallJob.schedule(1000, -1, [name: 'ConferenceCallJob', startDelay: 0])
        }
    }

    /**
     * Configure connectionpool to check connections
     *
     * @see http://java.dzone.com/news/database-connection-pooling
     * @see http://greybeardedgeek.net/2010/09/12/database-connection-pooling-in-grails-solving-the-idle-timeout-issue/
     */
    def configureDatasource(def applicationContext) {

        // Avoid dead connections in connectionpool

        //def ctx = servletContext.getAttribute(ApplicationAttributes.APPLICATION_CONTEXT)
        def dataSource = applicationContext.dataSourceUnproxied

        dataSource.setMinEvictableIdleTimeMillis(1000 * 60 * 30)
        dataSource.setTimeBetweenEvictionRunsMillis(1000 * 60 * 30)
        dataSource.setNumTestsPerEvictionRun(3)
        dataSource.setTestOnBorrow(true)
        dataSource.setTestWhileIdle(false)
        dataSource.setTestOnReturn(false)
        dataSource.setValidationQuery("SELECT 1")
    }

    void registerCustomJSONMarshallers() {
        JSON.registerObjectMarshaller(new CustomDomainClassJSONMarshaller(false, grailsApplication), 2)
        JSON.registerObjectMarshaller(new CustomGroovyBeanJSONMarshaller(), 1)
        JSON.registerObjectMarshaller(Date) {
            it == null ? null : ISODateTimeFormat.dateTime().print(new DateTime(it))
        }
    }
}
