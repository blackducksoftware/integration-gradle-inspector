/*
 * integration-gradle-inspector
 *
 * Copyright (c) 2022 Synopsys, Inc.
 *
 * Use subject to the terms and conditions of the Synopsys End User Software License and Maintenance Agreement. All rights reserved worldwide.
 */
package com.blackducksoftware.integration.gradle

import com.synopsys.integration.util.ExcludedIncludedFilter
import com.synopsys.integration.util.ExcludedIncludedWildcardFilter
import com.synopsys.integration.util.IntegrationEscapeUtil
import groovy.transform.TypeChecked
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.diagnostics.internal.dependencies.AsciiDependencyReportRenderer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@TypeChecked
class DependencyGatherer {
    private final Logger logger = LoggerFactory.getLogger(DependencyGatherer.class)

    IntegrationEscapeUtil integrationEscapeUtil = new IntegrationEscapeUtil()

    void createAllDependencyGraphFiles(final Project rootProject, String excludedProjectNames, String includedProjectNames, String excludedConfigurationNames, String includedConfigurationNames, File outputDirectory) {
        ExcludedIncludedFilter projectFilter = new ExcludedIncludedWildcardFilter(excludedProjectNames, includedProjectNames)
        ExcludedIncludedFilter configurationFilter = new ExcludedIncludedWildcardFilter(excludedConfigurationNames, includedConfigurationNames)

        File rootOutputFile = new File(outputDirectory, 'rootProjectMetadata.txt');
        String rootProjectGroup = rootProject.group.toString()
        String rootProjectName = rootProject.name.toString()
        String rootProjectVersionName = rootProject.version.toString()

        def rootProjectMetadataPieces = []
        rootProjectMetadataPieces.add('DETECT META DATA START')
        rootProjectMetadataPieces.add("rootProjectPath:${rootProject.getProjectDir().getCanonicalPath()}")
        rootProjectMetadataPieces.add("rootProjectGroup:${rootProjectGroup}")
        rootProjectMetadataPieces.add("rootProjectName:${rootProjectName}")
        rootProjectMetadataPieces.add("rootProjectVersion:${rootProjectVersionName}")
        rootProjectMetadataPieces.add('DETECT META DATA END')
        rootOutputFile << rootProjectMetadataPieces.join('\n')

        rootProject.allprojects.each { project ->
            if (projectFilter.shouldInclude(project.name)) {
                String group = project.group.toString()
                String name = project.name.toString()
                String version = project.version.toString()

                String nameForFile = integrationEscapeUtil.escapeForUri(name)
                File outputFile = new File(outputDirectory, "${nameForFile}_dependencyGraph.txt")
                if (outputFile.exists()) {
                    outputFile.delete()
                }

                outputFile.createNewFile()
                logger.info("starting ${outputFile.canonicalPath}")
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
                    if (configurationFilter.shouldInclude(configuration.name)) {
                        renderer.startConfiguration(configuration);
                        renderer.render(configuration);
                        renderer.completeConfiguration(configuration);
                    }
                }
                renderer.completeProject(project)
                renderer.complete()

                logger.info("adding meta data to ${outputFile.canonicalPath}")
                def metaDataPieces = []
                metaDataPieces.add('')
                metaDataPieces.add('DETECT META DATA START')
                metaDataPieces.add("rootProjectPath:${rootProject.getProjectDir().getCanonicalPath()}")
                metaDataPieces.add("rootProjectGroup:${rootProjectGroup}")
                metaDataPieces.add("rootProjectName:${rootProjectName}")
                metaDataPieces.add("rootProjectVersion:${rootProjectVersionName}")
                metaDataPieces.add("projectPath:${project.getProjectDir().getCanonicalPath()}")
                metaDataPieces.add("projectGroup:${group}")
                metaDataPieces.add("projectName:${name}")
                metaDataPieces.add("projectVersion:${version}")
                metaDataPieces.add('DETECT META DATA END')
                metaDataPieces.add('')

                outputFile << metaDataPieces.join('\n')

                logger.info("completed ${outputFile.canonicalPath}")
            }
        }
    }
}
