package org.opentele.server.citizen.model

import org.opentele.server.citizen.model.SemanticVersion
import spock.lang.Specification
import spock.lang.Unroll

class SemanticVersionSpec extends Specification {

    @Unroll
    def "can create a new version"() {
        when:
        def version = new SemanticVersion(versionString)

        then:
        version.major == major
        version.minor == minor
        version.patch == patch

        //some version strings, some from opentele-client-android:tags
        where:
        versionString       | major | minor | patch
        "1"                 | 1     | 0     | 0
        "1.2"               | 1     | 2     | 0
        "1.2.3"             | 1     | 2     | 3
        "0.0.0"             | 0     | 0     | 0
        null                | 0     | 0     | 0
        "1.6_PRE_1"         | 1     | 6     | 0
        "1.6_PRE_2"         | 1     | 6     | 0
        "1.7_PRE1"          | 1     | 7     | 0
        "OpenSource"        | 0     | 0     | 0
        "opensource-1.4.2"  | 1     | 4     | 2
        "abc1.2.3def"       | 1     | 2     | 3
    }

    @Unroll
    def "can compare two versions"() {
        when:
        def a = new SemanticVersion(aString)
        def b = new SemanticVersion(bString)

        then:
        (a <=> b) == compares
        (b <=> a) == -compares

        where:
        aString | bString   | compares
        "1.2.3" | "1.2.3"   | 0
        "1.2.3" | "1.2.4"   | -1
        "1.2.3" | "1.3.0"   | -1
        "1.2.3" | "2.0.0"   | -1
        "1.2.4" | "1.2.3"   | 1
        "2.0.0" | "1.2.3"   | 1
    }
}
