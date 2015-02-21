package org.opentele.server.citizen.model

//TODO would like to move this somewhere else Configuration/org.opentele.server, but that didn't work

// http://semver.org/
class SemanticVersion implements Comparable<SemanticVersion> {
    // will match any string with digits
    final semanticVersionRegexp = ~/(\d+)(\.(\d+)(\.(\d+))?)?/
    String version

    SemanticVersion(String version) {
        this.version = version ?: "0.0.0"
    }

    int getMajor() {
        value(1)
    }

    int getMinor() {
        value(3)
    }

    int getPatch() {
        value(5)
    }


    private int value(int groupNumber) {
        def groupContents = match()[0][groupNumber]
        groupContents == null ? 0 : groupContents as Integer
    }

    private match() {
        version =~ semanticVersionRegexp ?: [[null,0,0,0]]
    }

    @Override
    int compareTo(SemanticVersion other) {
        (major <=> other.major) ?:
            (minor <=> other.minor) ?:
                (patch <=> other.patch)
    }
}
