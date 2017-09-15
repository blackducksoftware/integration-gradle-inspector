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

import java.util.concurrent.CountDownLatch

import org.gradle.api.artifacts.ResolvedConfiguration
import org.gradle.api.artifacts.ResolvedDependency
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import com.blackducksoftware.integration.hub.bdio.simple.DependencyNodeBuilder
import com.blackducksoftware.integration.hub.bdio.simple.model.DependencyNode
import com.blackducksoftware.integration.hub.bdio.simple.model.externalid.MavenExternalId

class ConfigurationProcessor implements Runnable {
    private final Logger logger = LoggerFactory.getLogger(ConfigurationProcessor.class)
    private final CountDownLatch latch
    private final ResolvedConfiguration resolvedConfiguration
    private final String projectName
    private final String configuration
    private final DependencyNodeBuilder builder

    public ConfigurationProcessor(CountDownLatch latch, ResolvedConfiguration resolvedConfiguration, String projectName, String configuration, DependencyNodeBuilder builder) {
        this.latch = latch
        this.resolvedConfiguration = resolvedConfiguration
        this.projectName = projectName
        this.configuration = configuration
        this.builder=builder
    }

    public String getProjectName() {
        return projectName;
    }

    public String getConfiguration() {
        return configuration;
    }

    @Override
    public void run() {
        logger.info("Processing Project ${projectName}, Configuration ${configuration}")

        resolvedConfiguration.firstLevelModuleDependencies.each { dependency ->
            addDependencyNodeToParent(builder, null, dependency)
        }

        latch.countDown()
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
