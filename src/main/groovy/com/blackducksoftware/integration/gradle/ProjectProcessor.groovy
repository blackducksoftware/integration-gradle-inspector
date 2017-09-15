/*
 * Copyright (C) 2017 Black Duck Software Inc.
 * http://www.blackducksoftware.com/
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Black Duck Software ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Black Duck Software.
 */
package com.blackducksoftware.integration.gradle

import java.lang.reflect.Method
import java.util.concurrent.CountDownLatch

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolvedConfiguration
import org.gradle.api.artifacts.ResolvedDependency
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import com.blackducksoftware.integration.hub.bdio.simple.model.DependencyNode
import com.blackducksoftware.integration.hub.bdio.simple.model.externalid.MavenExternalId
import com.blackducksoftware.integration.hub.detect.model.BomToolType
import com.blackducksoftware.integration.hub.detect.model.DetectCodeLocation
import com.blackducksoftware.integration.util.ExcludedIncludedFilter
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.stream.JsonWriter

class ProjectProcessor implements Runnable {
    private final Logger logger = LoggerFactory.getLogger(ProjectProcessor.class)
    private final CountDownLatch latch
    private final Project project

    private final ExcludedIncludedFilter configurationFilter
    private final File outputDirectory

    private final  String projectGroup
    private final  String projectName
    private final  String projectVersionName

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create()

    public ProjectProcessor(CountDownLatch latch, Project project, ExcludedIncludedFilter configurationFilter, File outputDirectory, String projectGroup, String projectName, String projectVersionName) {
        this.latch = latch
        this.project = project
        this.configurationFilter=configurationFilter
        this.outputDirectory = outputDirectory
        this.projectGroup = projectGroup
        this.projectName = projectName
        this.projectVersionName = projectVersionName
    }


    @Override
    public void run() {
        try {
            logger.info("Processing Project ${project.name}, Configurations ${project.configurations.size()}")

            def group = project.group.toString()
            def name = project.name.toString()
            def version = project.version.toString()
            DependencyNode projectNode = new DependencyNode(name, version, new MavenExternalId(group, name, version))


            //DependencyNodeBuilder builder = new DependencyNodeBuilder(projectNode)

            project.configurations.each { configuration ->
                ResolvedConfiguration resolvedConfiguration = resolveConfiguration(configuration, configurationFilter)
                if (resolvedConfiguration != null) {
                    logger.info("Processing Project ${name}, Configuration ${configuration.name}")
                    resolvedConfiguration.firstLevelModuleDependencies.each { dependency ->
                        addDependencyNodeToParent(projectNode, dependency)
                    }
                }
            }

            logger.info("Finished processing the configurations for Project ${name}")

            File outputFile = new File(outputDirectory, "${group}_${name}_detectCodeLocation.json")
            if (outputFile.exists()) {
                outputFile.delete()
            }
            DetectCodeLocation codeLocation = new DetectCodeLocation(BomToolType.GRADLE, project.getProjectDir().getAbsolutePath(), projectName, projectVersionName,
                    new MavenExternalId(projectGroup, projectName, projectVersionName),projectNode.children)

            JsonWriter jsonWriter = gson.newJsonWriter(new BufferedWriter(new FileWriter(outputFile)))
            gson.toJson(codeLocation, DetectCodeLocation.class, jsonWriter)
            jsonWriter.close()

            logger.info("Finished Project code location file ${outputFile.getAbsolutePath()}")
        } catch (Exception e) {
            logger.error(e.getMessage(), e)
            throw e;
        } finally {
            latch.countDown()
        }
    }

    private ResolvedConfiguration resolveConfiguration(Configuration configuration, ExcludedIncludedFilter configurationFilter) {
        if (!configurationFilter.shouldInclude(configuration.name)) {
            return null
        }
        try {
            Method isCanBeResolved = Configuration.class.getMethod("isCanBeResolved")
            boolean result = isCanBeResolved.invoke(configuration)
            if (result) {
                return configuration.resolvedConfiguration
            } else {
                return null
            }
        } catch (Exception e) {
            //Exceptions are likely here since we are trying to invoke a method that may not exist (isCanBeResolved was added in 3.3)
            logger.debug("Trying to invoke isCanBeResolved threw an Exception (likely not an issue): ${e.message}")
        }

        try {
            return configuration.resolvedConfiguration
        } catch (Exception e) {
            //Exceptions are unlikely here since there should not be any configurations that can not be resolved without the isCanBeResolved method
            logger.error("Tried to resolve a configuration that can't be resolved and the isCanBeResolved method doesn't appear to exist: ${e.message}")
        }
        return null
    }

    private void addDependencyNodeToParent(DependencyNode parentDependencyNode, final ResolvedDependency resolvedDependency) {
        def group = resolvedDependency.moduleGroup
        def name = resolvedDependency.moduleName
        def version = resolvedDependency.moduleVersion

        def mavenExternalId = new MavenExternalId(group, name, version)
        def dependencyNode = new DependencyNode(name, version, mavenExternalId)

        parentDependencyNode.children.add(dependencyNode)


        for (ResolvedDependency child : resolvedDependency.getChildren()) {
            addDependencyNodeToParent(dependencyNode, child)
        }
    }
}
