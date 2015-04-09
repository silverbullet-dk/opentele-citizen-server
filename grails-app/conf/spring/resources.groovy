import grails.plugin.springsecurity.SpringSecurityUtils
import grails.util.Environment
import org.opentele.server.core.UserDetailsService
import org.opentele.server.core.model.BootStrapUtil
import org.opentele.server.core.util.CaseInsensitivePasswordAuthenticationProvider
import org.opentele.server.core.util.CustomPropertyEditorRegistrar
import org.opentele.server.core.util.OpenTeleSecurityBadCredentialsEventListener
import org.opentele.server.core.util.OpenTeleSecurityGoodAttemptEventListener
import org.opentele.server.core.util.OpenteleSecurityBasicAuthenticationFilter
import org.opentele.server.provider.NonAuthenticationFilter
import org.opentele.server.provider.OpenteleAuditLogLookup
import org.springframework.security.core.session.SessionRegistryImpl
import org.springframework.security.web.authentication.session.ConcurrentSessionControlStrategy
import org.springframework.security.web.session.ConcurrentSessionFilter
import org.springframework.web.servlet.i18n.SessionLocaleResolver
import wslite.http.HTTPClient
import wslite.soap.SOAPClient

// Place your Spring DSL code here
beans = {
    nonAuthFilter(NonAuthenticationFilter)

    localeResolver(SessionLocaleResolver) {
        defaultLocale = grailsApplication.config.defaultLocale
        Locale.setDefault(grailsApplication.config.defaultLocale)

        customPropertyEditorRegistrar(CustomPropertyEditorRegistrar)
        auditLogLookupBean(OpenteleAuditLogLookup)
        userDetailsService(UserDetailsService)
    }
    caseInsensitivePasswordAuthenticationProvider(CaseInsensitivePasswordAuthenticationProvider) {
        userDetailsService = ref('userDetailsService')
        passwordEncoder = ref('passwordEncoder')
        userCache = ref('userCache')
        saltSource = ref('saltSource')
        preAuthenticationChecks = ref('preAuthenticationChecks')
        postAuthenticationChecks = ref('postAuthenticationChecks')
        hideUserNotFoundExceptions = SpringSecurityUtils.securityConfig.dao.hideUserNotFoundExceptions
    }

    openTeleSecurityBadCredentialsEventListener(OpenTeleSecurityBadCredentialsEventListener)
    openTeleSecurityGoodAttemptEventListener(OpenTeleSecurityGoodAttemptEventListener)
    basicAuthenticationFilter(OpenteleSecurityBasicAuthenticationFilter) {
        authenticationManager = ref('authenticationManager')
        authenticationEntryPoint = ref('basicAuthenticationEntryPoint')
    }

    if (grailsApplication.config.milou.run) {
        milouHttpClient(HTTPClient) {
            connectTimeout = 5000
            readTimeout = 10000
            useCaches = false
            followRedirects = false
            sslTrustAllCerts = true
        }

        milouSoapClient(SOAPClient) {
            serviceURL = grailsApplication.config.milou.serverURL
            httpClient = ref('milouHttpClient')
        }
    }

    sessionRegistry(SessionRegistryImpl)

    sessionAuthenticationStrategy(ConcurrentSessionControlStrategy, sessionRegistry) {
        maximumSessions = -1
    }

    concurrentSessionFilter(ConcurrentSessionFilter){
        sessionRegistry = sessionRegistry
        expiredUrl = '/login/concurrentSession'
    }

    if(Environment.current.name == 'development' && !BootStrapUtil.isH2DatabaseServerRunning("jdbc:h2:tcp://localhost:8043/clinicianDb", "sa", "")) {
        h2Server(org.h2.tools.Server, "-tcp,-tcpPort,8043") { bean ->
            bean.factoryMethod = "createTcpServer"
            bean.initMethod = "start"
            bean.destroyMethod = "stop"
        }
    }
}