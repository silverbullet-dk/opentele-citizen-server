import org.apache.log4j.DailyRollingFileAppender

grails.config.locations = ["file:${userHome}/.opentele/citizen-config.properties"]

logging.suffix = ""

grails.session.timeout.default = 30

grails.project.groupId = appName

grails.mime.file.extensions = true
grails.mime.use.accept.header = true
grails.mime.types = [ html: ['text/html','application/xhtml+xml'],
                      xml: ['text/xml', 'application/xml'],
                      text: 'text/plain',
                      js: 'text/javascript',
                      rss: 'application/rss+xml',
                      atom: 'application/atom+xml',
                      css: 'text/css',
                      csv: 'text/csv',
                      all: '*/*',
                      json: ['application/json','text/json'],
                      form: 'application/x-www-form-urlencoded',
                      multipartForm: 'multipart/form-data'
]

// URL Mapping Cache Max Size, defaults to 5000
//grails.urlmapping.cache.maxsize = 1000

// What URL patterns should be processed by the resources plugin
grails.resources.adhoc.patterns = ['/images/*', '/css/*', '/js/*', '/plugins/*']



// The default codec used to encode data with ${}
grails.views.default.codec = "none" // none, html, base64
grails.views.gsp.encoding = "UTF-8"
grails.converters.encoding = "UTF-8"

// enable Sitemesh preprocessing of GSP pages
grails.views.gsp.sitemesh.preprocess = true
// scaffolding templates configuration
grails.scaffolding.templates.domainSuffix = 'Instance'

// Set to false to use the new Grails 1.2 JSONBuilder in the render method
grails.json.legacy.builder = false
// enabled native2ascii conversion of i18n properties files
grails.enable.native2ascii = true
// packages to include in Spring bean scanning
grails.spring.bean.packages = []
// whether to disable processing of multi part requests
grails.web.disable.multipart=false

// request parameters to mask when logging exceptions
grails.exceptionresolver.params.exclude = ['password']

// enable query caching by default
grails.hibernate.cache.queries = true

help {
    image {
        contentTypes = ["image/jpeg","image/pjpeg","image/png","image/x-png"]
        uploadPath = "../helpimages" //In production this path should be configured to a full local path. NOT a path relatvie to web-app!
        providedImagesPath = "../helpimages"
        overwriteExistingFiles = true
    }
}

defaultLocale = new Locale("da", "DK")

// CORS setup
cors.url.pattern = '*'
cors.expose.headers = 'AccountIsLocked'
cors.headers = ['Access-Control-Allow-Headers': "origin, authorization, accept, content-type, x-requested-with, client-version"]

def pluginDir = org.codehaus.groovy.grails.plugins.GrailsPluginUtils.getPluginDirForName('opentele-server-core-plugin')?.path
grails.plugin.databasemigration.changelogLocation = "$pluginDir/grails-app/migrations"

grails.plugin.databasemigration.dropOnStart = false
grails.plugin.databasemigration.updateOnStart = false

// set per-environment serverURL stem for creating absolute links
environments {
    development {
        grails.logging.jul.usebridge = true
    }
    performance {
        grails.logging.jul.usebridge = true
    }
    test {
        grails.logging.jul.usebridge = true
    }
}


// Logging
String logDirectory = "${System.getProperty('catalina.base') ?: '.'}/logs"
String commonPattern = "%d [%t] %-5p %c{2} %x - %m%n"
def appContext = { logging.suffix != "" ? "-${logging.suffix}" : "" }

log4j = {
    appenders {
        console name: "stdout",
                layout: pattern(conversionPattern: commonPattern)
        appender new DailyRollingFileAppender(
                name:"stacktrace", datePattern: "'.'yyyy-MM-dd",
                file:"${logDirectory}/stacktrace-citizen${appContext()}.log",
                layout: pattern(conversionPattern: commonPattern))
        appender new DailyRollingFileAppender(
                name:"opentele", datePattern: "'.'yyyy-MM-dd",
                file:"${logDirectory}/opentele-citizen${appContext()}.log",
                layout: pattern(conversionPattern: commonPattern))
    }

    error  'org.codehaus.groovy.grails.web.servlet',  //  controllers
            'org.codehaus.groovy.grails.web.pages', //  GSP
            'org.codehaus.groovy.grails.web.sitemesh', //  layouts
            'org.codehaus.groovy.grails.web.mapping.filter', // URL mapping
            'org.codehaus.groovy.grails.web.mapping', // URL mapping
            'org.codehaus.groovy.grails.commons', // core / classloading
            'org.codehaus.groovy.grails.plugins', // plugins
            'org.codehaus.groovy.grails.orm.hibernate', // hibernate integration,
            'org.codehaus.groovy.grails.commons.cfg',
            'org.springframework',
            'org.hibernate',
            'org.apache',
            'net.sf.ehcache.hibernate',
            'grails.app.services.org.grails.plugin.resource',
            'grails.app.taglib.org.grails.plugin.resource',
            'grails.app.resourceMappers.org.grails.plugin.resource',
            'grails.app.service.grails.buildtestdata.BuildTestDataService',
            'grails.app.buildtestdata',
            'grails.app.services.grails.buildtestdata',
            'grails.buildtestdata.DomainInstanceBuilder'

    root {
        error 'opentele', 'stdout'
    }

    environments {
        development {
            debug 'grails.app',
                    'org.opentele',
                    'grails.app.jobs'
        }
        performance {
            debug 'grails.app',
                    'org.opentele'
        }
        test {
            debug 'grails.app',
                    'org.opentele'
        }
    }
}

// Added by the Spring Security Core plugin:
grails.plugin.springsecurity.userLookup.userDomainClassName = 'org.opentele.server.model.User'
grails.plugin.springsecurity.userLookup.authorityJoinClassName = 'org.opentele.server.model.UserRole'
grails.plugin.springsecurity.authority.className = 'org.opentele.server.model.Role'
grails.plugin.springsecurity.useBasicAuth = true
grails.plugin.springsecurity.basic.realmName = "OpenTele Server"
grails.plugin.springsecurity.useSecurityEventListener = true
grails.plugin.springsecurity.password.algorithm = 'SHA-256'
grails.plugin.springsecurity.password.hash.iterations = 1

grails.plugin.springsecurity.securityConfigType = 'Annotation'
grails.plugin.springsecurity.controllerAnnotations.staticRules = [
        '/patient-api.html': ['IS_AUTHENTICATED_ANONYMOUSLY']
]

grails.plugin.springsecurity.filterChain.chainMap = [
        '/currentVersion': 'nonAuthFilter',
        '/patient-api.html': 'nonAuthFilter',
        '/isAlive': 'nonAuthFilter',
        '/isAlive/json': 'nonAuthFilter',
        '/isAlive/html': 'nonAuthFilter',
        '/**': 'JOINED_FILTERS,-exceptionTranslationFilter,-sessionManagementFilter'
]
grails.plugin.springsecurity.providerNames = [
        'caseInsensitivePasswordAuthenticationProvider',
        'anonymousAuthenticationProvider',
        'rememberMeAuthenticationProvider'
]

passwordRetryGracePeriod=120
passwordMaxAttempts=3
reminderEveryMinutes=15
