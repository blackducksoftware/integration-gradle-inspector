package com.blackducksoftware.integration.gradle

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.diagnostics.internal.dependencies.AsciiDependencyReportRenderer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import com.blackducksoftware.integration.util.ExcludedIncludedFilter

import groovy.transform.TypeChecked

@TypeChecked
class DependencyGatherer {
    private final Logger logger = LoggerFactory.getLogger(DependencyGatherer.class)

    def alreadyAddedIds = new HashSet<>()
    def componentCounts = new HashMap<String, Integer>()

    void createAllDependencyGraphFiles(final Project rootProject, String excludedProjectNames, String includedProjectNames, String excludedConfigurationNames, String includedConfigurationNames, File outputDirectory) {
        ExcludedIncludedFilter projectFilter = new ExcludedIncludedFilter(excludedProjectNames, includedProjectNames)
        ExcludedIncludedFilter configurationFilter = new ExcludedIncludedFilter(excludedConfigurationNames, includedConfigurationNames)

        String rootProjectGroup = rootProject.group.toString()
        String rootProjectName = rootProject.name.toString()
        String rootProjectVersionName = rootProject.version.toString()

        rootProject.allprojects.each { project ->
            if (projectFilter.shouldInclude(project.name)) {
                String group = project.group.toString()
                String name = project.name.toString()
                String version = project.version.toString()

                File outputFile = new File(outputDirectory, "${group}_${name}_dependencyGraph.txt")
                if (outputFile.exists()) {
                    outputFile.delete()
                }
                outputFile.createNewFile()

                println "starting ${outputFile.canonicalPath}"
                AsciiDependencyReportRenderer renderer = new AsciiDependencyReportRenderer()
                renderer.setOutputFile(outputFile)
                renderer.startProject(project)

                SortedSet<Configuration> sortedConfigurations = new TreeSet<Configuration>(new Comparator<Configuration>() {
                            public int compare(Configuration conf1, Configuration conf2) {
                                return conf1.getName().compareTo(conf2.getName());
                            }
                        });
                sortedConfigurations.addAll(project.configurations);
                for (Configuration configuration : sortedConfigurations) {
                    if(configurationFilter.shouldInclude(configuration.name)) {
                        renderer.startConfiguration(configuration);
                        renderer.render(configuration);
                        renderer.completeConfiguration(configuration);
                    }
                }
                renderer.completeProject(project)
                renderer.complete()

                println "completed ${outputFile.canonicalPath}"
            }
        }
    }
}
