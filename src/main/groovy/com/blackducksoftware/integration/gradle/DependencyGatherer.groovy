/*
 * integration-gradle-inspector
 *
 * Copyright (C) 2018 Black Duck Software, Inc.
 * http://www.blackducksoftware.com/
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.blackducksoftware.integration.gradle

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.diagnostics.internal.dependencies.AsciiDependencyReportRenderer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import com.blackducksoftware.integration.util.ExcludedIncludedFilter
import com.blackducksoftware.integration.util.IntegrationEscapeUtil

import groovy.transform.TypeChecked

@TypeChecked
class DependencyGatherer {
    private final Logger logger = LoggerFactory.getLogger(DependencyGatherer.class)

    IntegrationEscapeUtil integrationEscapeUtil = new IntegrationEscapeUtil()

    void createAllDependencyGraphFiles(final Project rootProject, String excludedProjectNames, String includedProjectNames, String excludedConfigurationNames, String includedConfigurationNames, File outputDirectory) {
        ExcludedIncludedFilter projectFilter = new ExcludedIncludedFilter(excludedProjectNames, includedProjectNames)
        ExcludedIncludedFilter configurationFilter = new ExcludedIncludedFilter(excludedConfigurationNames, includedConfigurationNames)

        File rootOutputFile = new File(outputDirectory, 'rootProjectMetadata.txt');
        def rootProjectMetadataPieces = []
        rootProjectMetadataPieces.add('DETECT META DATA START')
        rootProjectMetadataPieces.add("rootProjectPath:${rootProject.getProjectDir().getCanonicalPath()}")
        rootProjectMetadataPieces.add("rootProjectGroup:${rootProject.group.toString()}")
        rootProjectMetadataPieces.add("rootProjectName:${rootProject.name.toString()}")
        rootProjectMetadataPieces.add("rootProjectVersion:${rootProject.version.toString()}")
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
