package com.blackducksoftware.integration.gradle

import java.lang.reflect.Method
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolvedConfiguration
import org.gradle.api.artifacts.ResolvedDependency
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import com.blackducksoftware.integration.hub.bdio.simple.DependencyNodeBuilder
import com.blackducksoftware.integration.hub.bdio.simple.model.DependencyNode
import com.blackducksoftware.integration.hub.bdio.simple.model.externalid.MavenExternalId
import com.blackducksoftware.integration.hub.detect.model.BomToolType
import com.blackducksoftware.integration.hub.detect.model.DetectCodeLocation
import com.blackducksoftware.integration.util.ExcludedIncludedFilter
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.stream.JsonWriter

class DependencyGatherer {
    private final Logger logger = LoggerFactory.getLogger(DependencyGatherer.class)

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create()


    void createAllCodeLocationFiles(final Project rootProject, String excludedProjectNames, String includedProjectNames, String excludedConfigurationNames, String includedConfigurationNames, File outputDirectory) {
        multiThreadedProjects(rootProject, excludedProjectNames, includedProjectNames, excludedConfigurationNames, includedConfigurationNames, outputDirectory)
    }

    void multiThreadedProjects(final Project rootProject, String excludedProjectNames, String includedProjectNames, String excludedConfigurationNames, String includedConfigurationNames, File outputDirectory) {
        try {
            ExcludedIncludedFilter projectFilter = new ExcludedIncludedFilter(excludedProjectNames, includedProjectNames)
            ExcludedIncludedFilter configurationFilter = new ExcludedIncludedFilter(excludedConfigurationNames, includedConfigurationNames)

            String projectGroup = rootProject.group.toString()
            String projectName = rootProject.name.toString()
            String projectVersionName = rootProject.version.toString()
            CountDownLatch latch = new CountDownLatch(rootProject.allprojects.size())
            logger.info("Processing ${rootProject.allprojects.size()} projects")

            int processors = Runtime.getRuntime().availableProcessors();
            logger.info("Processors ${processors}")
            ExecutorService executor = Executors.newFixedThreadPool(processors);


            rootProject.allprojects.each { project ->
                if (projectFilter.shouldInclude(project.name)) {
                    ProjectProcessor projectProcessor = new ProjectProcessor(latch, project, configurationFilter, outputDirectory,  projectGroup,  projectName,  projectVersionName,  gson)
                    executor.submit(projectProcessor);
                }
            }
            try {
                latch.await();
            } catch (InterruptedException e) {
                logger.error(e.getMessage(), e)
            }

            logger.info("Finished processing the all Projects")
        } catch (Exception e) {
            logger.error(e.getMessage(), e)
            throw e;
        }
    }

    void multiThreadedConfigurations(final Project rootProject, String excludedProjectNames, String includedProjectNames, String excludedConfigurationNames, String includedConfigurationNames, File outputDirectory) {
        try {
            ExcludedIncludedFilter projectFilter = new ExcludedIncludedFilter(excludedProjectNames, includedProjectNames)
            ExcludedIncludedFilter configurationFilter = new ExcludedIncludedFilter(excludedConfigurationNames, includedConfigurationNames)

            String projectGroup = ''
            String projectName = ''
            String projectVersionName = ''
            rootProject.allprojects.each { project ->
                if (projectFilter.shouldInclude(project.name)) {
                    logger.info("Processing Project ${project.name}")
                    def group = project.group.toString()
                    def name = project.name.toString()
                    def version = project.version.toString()
                    if (!projectGroup) {
                        projectGroup = group
                    }
                    if (!projectName) {
                        projectName = name
                    }
                    if (!projectVersionName) {
                        projectVersionName = version
                    }
                    DependencyNode projectNode = new DependencyNode(name, version, new MavenExternalId(group, name, version))

                    CountDownLatch latch = new CountDownLatch(project.configurations.size())

                    int processors = Runtime.getRuntime().availableProcessors();
                    ExecutorService executor = Executors.newFixedThreadPool(processors);

                    List<ConfigurationProcessor> configurationProcessors = new ArrayList<>()

                    DependencyNodeBuilder builder = new DependencyNodeBuilder(projectNode)

                    project.configurations.each { configuration ->
                        ResolvedConfiguration resolvedConfiguration = resolveConfiguration(configuration, configurationFilter)
                        if (resolvedConfiguration != null) {
                            ConfigurationProcessor configurationProcessor = new ConfigurationProcessor(latch, resolvedConfiguration, name, configuration.name, builder)
                            configurationProcessors.add(configurationProcessor)
                            executor.submit(configurationProcessor);
                        }
                    }
                    try {
                        latch.await();
                    } catch (InterruptedException e) {
                        logger.error(e.getMessage(), e)
                    }

                    logger.info("Finished processing the configurations for Project ${name}")

                    File outputFile = new File(outputDirectory, "${group}_${name}_detectCodeLocation.json")
                    if (outputFile.exists()) {
                        outputFile.delete()
                    }
                    DetectCodeLocation codeLocation = new DetectCodeLocation(BomToolType.GRADLE, project.getProjectDir().getAbsolutePath(), projectName, projectVersionName,
                            new MavenExternalId(projectGroup, projectName, projectVersionName),builder.root.children)

                    JsonWriter jsonWriter = gson.newJsonWriter(new BufferedWriter(new FileWriter(outputFile)))
                    gson.toJson(codeLocation, DetectCodeLocation.class, jsonWriter)
                    jsonWriter.close()
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e)
            throw e;
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

    private void addDependencyNodesToBuilder(DependencyNodeBuilder builder, DependencyNode parent, final List<DependencyNode> children) {
        builder.addParentNodeWithChildren(parent, children)
        for (DependencyNode child : children) {
            addDependencyNodesToBuilder(builder, child, child.children)
        }
    }

    private void addDependencyNodeToParent(DependencyNodeBuilder builder, DependencyNode parentDependencyNode, final ResolvedDependency resolvedDependency) {
        def group = resolvedDependency.moduleGroup
        def name = resolvedDependency.moduleName
        def version = resolvedDependency.moduleVersion

        def mavenExternalId = new MavenExternalId(group, name, version)
        def dependencyNode = new DependencyNode(name, version, mavenExternalId)

        if(parentDependencyNode == null) {
            builder.addParentNodeWithChildren(builder.root, [dependencyNode])
        } else {
            builder.addParentNodeWithChildren(parentDependencyNode, [dependencyNode])
        }
        for (ResolvedDependency child : resolvedDependency.getChildren()) {
            addDependencyNodeToParent(builder, dependencyNode, child)
        }
    }
}
