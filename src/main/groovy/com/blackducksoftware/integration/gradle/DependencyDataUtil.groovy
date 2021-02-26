/*
 * integration-gradle-inspector
 *
 * Copyright (c) 2021 Synopsys, Inc.
 *
 * Use subject to the terms and conditions of the Synopsys End User Software License and Maintenance Agreement. All rights reserved worldwide.
 */
package com.blackducksoftware.integration.gradle

import com.synopsys.integration.util.ExcludedIncludedFilter
import com.synopsys.integration.util.ExcludedIncludedWildcardFilter
import com.synopsys.integration.util.IntegrationEscapeUtil
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration

class DependencyDataUtil {
    IntegrationEscapeUtil integrationEscapeUtil = new IntegrationEscapeUtil()

    void generateRootProjectMetaData(Project project, String outputDirectoryPath) {
        File outputDirectory = createTaskOutputDirectory(outputDirectoryPath)
        outputDirectory.mkdirs()

        Project rootProject = project.gradle.rootProject;
        /* if the current project is the root project then generate the file containing
           the meta data for the root project otherwise ignore.
         */
        if (project.name.equals(rootProject.name)) {
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
        }
    }

    Set<Configuration> filterConfigurations(Project project, String excludedConfigurationNames, String includedConfigurationNames) {
        ExcludedIncludedFilter configurationFilter = new ExcludedIncludedWildcardFilter(excludedConfigurationNames, includedConfigurationNames)
        Set<Configuration> filteredConfigurationSet = new TreeSet<Configuration>(new Comparator<Configuration>() {
            public int compare(Configuration conf1, Configuration conf2) {
                return conf1.getName().compareTo(conf2.getName());
            }
        })
        for (Configuration configuration : project.configurations) {
            if (configurationFilter.shouldInclude(configuration.name)) {
                filteredConfigurationSet.add(configuration)
            }
        }

        filteredConfigurationSet
    }

    Optional<File> getProjectOutputFile(Project project, String outputDirectoryPath, String excludedProjectNames, String includedProjectNames) {
        Optional<File> projectOutputFile = Optional.empty()
        ExcludedIncludedFilter projectFilter = new ExcludedIncludedWildcardFilter(excludedProjectNames, includedProjectNames)
        // check if the project should be included.  If it should be included then return the file that will contain dependency data
        if (projectFilter.shouldInclude(project.name)) {
            File outputDirectory = createTaskOutputDirectory(outputDirectoryPath)
            String name = project.name.toString()

            String nameForFile = integrationEscapeUtil.escapeForUri(name)
            File outputFile = new File(outputDirectory, "${nameForFile}_dependencyGraph.txt")
            projectOutputFile = Optional.of(outputFile)
        }
        projectOutputFile
    }

    File createProjectOutputFile(File projectFile) {
        if (projectFile.exists()) {
            projectFile.delete()
        }

        projectFile.createNewFile()
        projectFile
    }

    void appendProjectMetadata(Project project, File projectOutputFile) {
        Project rootProject = project.gradle.rootProject;
        String rootProjectGroup = rootProject.group.toString()
        String rootProjectName = rootProject.name.toString()
        String rootProjectVersionName = rootProject.version.toString()
        String group = project.group.toString()
        String name = project.name.toString()
        String version = project.version.toString()

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

        projectOutputFile << metaDataPieces.join('\n')
    }

    private File createTaskOutputDirectory(String outputDirectoryPath) {
        File outputDirectory = new File(outputDirectoryPath)
        outputDirectory.mkdirs()

        outputDirectory
    }
}
