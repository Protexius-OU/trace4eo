# trace4eo

TODO

## Prerequisites

* Java 21

## Running via Docker



## Static code analysis

### Checkstyle (http://checkstyle.sourceforge.net/)

Checkstyle is used to maintain a consistent code style.
Checkstyle is configured to run automatically in Gradle compile phase.

### JaCoCo (https://www.eclemma.org/jacoco/)

Code test coverage can be measured by running `./gradlew jacocoTestReport`.

Report is generated to `build/reports/jacoco/` directory.

### SpotBugs (https://spotbugs.github.io/)

SpotBugs analyzes bytecode to find common bugs and code problems. This is done automatically when
running `./gradlew build`

XML and HTML reports can be found in `build/reports/spotbugs/` directory.

### OpenRewrite (https://docs.openrewrite.org/)

Run `./gradlew rewriteRun` to automatically fix some checkstyle errors.

#### OWASP Dependency-Check

Dependency-Check is a utility that identifies project dependencies
and checks if there are any known, publicly disclosed, vulnerabilities.

To run the analysis, configure `dependencyCheck.nvd.apiKey` in your local `gradle.properties` file and run `./gradlew dependencyCheckAnalyze`
