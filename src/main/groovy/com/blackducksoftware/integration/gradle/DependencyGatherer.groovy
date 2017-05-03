package com.blackducksoftware.integration.gradle

import org.gradle.api.Project
import org.gradle.api.artifacts.ResolvedDependency
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import com.blackducksoftware.integration.hub.bdio.simple.DependencyNodeBuilder
import com.blackducksoftware.integration.hub.bdio.simple.model.DependencyNode
import com.blackducksoftware.integration.hub.bdio.simple.model.externalid.MavenExternalId
import com.blackducksoftware.integration.util.ExcludedIncludedFilter

class DependencyGatherer {
    private final Logger logger = LoggerFactory.getLogger(DependencyGatherer.class)

    private Set<String> alreadyAddedIds
    private DependencyNodeBuilder dependencyNodeBuilder

    DependencyNode getFullyPopulatedRootNode(final Project rootProject, String excludedProjectNames, String includedProjectNames, String excludedConfigurationNames, String includedConfigurationNames) {
        def group = rootProject.group
        def name = rootProject.name
        def version = rootProject.version
        DependencyNode rootProjectNode = new DependencyNode(name, version, new MavenExternalId(group, name, version))

        ExcludedIncludedFilter projectFilter = new ExcludedIncludedFilter(excludedProjectNames, includedProjectNames)
        ExcludedIncludedFilter configurationFilter = new ExcludedIncludedFilter(excludedConfigurationNames, includedConfigurationNames)
        alreadyAddedIds = new HashSet<>()
        dependencyNodeBuilder = new DependencyNodeBuilder(rootProjectNode)

        rootProject.allprojects.each { project ->
            if (projectFilter.shouldInclude(project.name)) {
                project.configurations.each { configuration ->
                    if (configurationFilter.shouldInclude(configuration.name)) {
                        configuration.resolvedConfiguration.firstLevelModuleDependencies.each { dependency ->
                            addDependencyNodeToParent(configuration.name, rootProjectNode, dependency)
                        }
                    }
                }
            }
        }

        return rootProjectNode;
    }

    private void addDependencyNodeToParent(String configurationName, DependencyNode parentDependencyNode, final ResolvedDependency resolvedDependency) {
        def group = resolvedDependency.moduleGroup
        def name = resolvedDependency.moduleName
        def version = resolvedDependency.moduleVersion

        def mavenExternalId = new MavenExternalId(group, name, version)
        def dependencyNode = new DependencyNode(name, version, mavenExternalId)
        dependencyNodeBuilder.addChildNodeWithParents(dependencyNode, [parentDependencyNode])
        if (alreadyAddedIds.add(mavenExternalId.createDataId())) {
            for (ResolvedDependency child : resolvedDependency.getChildren()) {
                /**
                 * A ResolvedDependency will include ALL children from ALL Configurations, regardless of the Configuration it came from
                 */
                if (configurationName == child.configuration) {
                    addDependencyNodeToParent(configurationName, dependencyNode, child)
                }
            }
        }
    }
}
