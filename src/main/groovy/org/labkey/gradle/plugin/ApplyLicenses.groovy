package org.labkey.gradle.plugin

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.bundling.Jar
import org.labkey.gradle.util.BuildUtils
import org.labkey.gradle.util.GroupNames

class ApplyLicenses implements Plugin<Project>
{
    @Override
    void apply(Project project)
    {
        if (project.findProject(BuildUtils.getApiProjectPath(project.gradle)))
            project.evaluationDependsOn(BuildUtils.getApiProjectPath(project.gradle))
        addConfigurations(project)
        addDependencies(project)
        addTasks(project)
    }

    private void addConfigurations(Project project)
    {
        project.configurations
        {
            extJs3Commercial
            extJs4Commercial
            licensePatch {
                canBeConsumed = true
                canBeResolved = true
            }
        }
        project.configurations.extJs3Commercial.setDescription("extJs 3 commercial license libraries")
        project.configurations.extJs4Commercial.setDescription("extJs 4 commercial license libraries")
        project.configurations.licensePatch.setDescription("Modules that require patching with commercial-license libraries")
    }


    private void addDependencies(Project project)
    {
        if (!BuildUtils.isOpenSource(project)) {
            project.dependencies {
                // Can't have two versions of the same dependency in one configuration
                extJs4Commercial "com.sencha.extjs:extjs:4.2.1:commercial@zip"
                extJs3Commercial "com.sencha.extjs:extjs:3.4.1:commercial@zip"
            }

            BuildUtils.addLabKeyDependency(project, "licensePatch", BuildUtils.getApiProjectPath(project.gradle), "published", project.getVersion().toString(), "module")
        }
    }

    private static void addTasks(Project project)
    {
        if (!BuildUtils.isOpenSource(project)) {
            var patchApiTask = project.tasks.register('patchApiModule', Jar) {
                Jar jar ->
                    jar.group = GroupNames.DISTRIBUTION
                    jar.description = "Patches the api module to replace ExtJS libraries with commercial versions"
                    jar.archiveBaseName.set("api")
                    jar.archiveVersion.set(project.getVersion().toString())
                    jar.archiveClassifier.set("extJsCommercial")
                    jar.archiveExtension.set('module')
                    jar.destinationDirectory.set(project.layout.buildDirectory.dir("patchApiModule"))
                    jar.outputs.cacheIf({ true })
                    // first include the ext-3.4.1 and ext-4.2.1 directories from the extjs configuration artifacts
                    jar.into('web') {
                        from project.configurations.extJs3Commercial.collect {
                            project.zipTree(it)
                        }
                    }
                    jar.into('web') {
                        from project.configurations.extJs4Commercial.collect {
                            project.zipTree(it)
                        }
                    }
                    // include the original module file ...
                    jar.from project.configurations.licensePatch.collect {
                        project.zipTree(it).matching {
                            // DuplicatesStrategy.EXCLUDE doesn't seem to work in some environments
                            exclude('web/ext-*/**')
                        }
                    }
                    // ... but don't use the ext directories that come from that file
                    jar.setDuplicatesStrategy(DuplicatesStrategy.EXCLUDE)
                    jar.manifest.attributes(
                            "Implementation-Version": project.version,
                            "Implementation-Title": "Internal API classes",
                            "Implementation-Vendor": "LabKey"
                    )
                    if (project.findProject(BuildUtils.getApiProjectPath(project.gradle))) {
                        var apiProj = project.project(BuildUtils.getApiProjectPath(project.gradle))
                        jar.dependsOn(apiProj.tasks.named("module"))
                        jar.archiveVersion.set(apiProj.getVersion().toString())
                    }
            }

            project.tasks.register('verifyLicensePatch') {
                dependsOn(patchApiTask)
                doLast {
                    [project.configurations.extJs3Commercial, project.configurations.extJs4Commercial].forEach {
                        def commercialLicense = project.zipTree(it.singleFile).matching {
                            include '*/license.txt'
                        }.singleFile
                        def patchedLicense = project.zipTree(patchApiTask.get().outputs.files.singleFile).matching {
                            include 'web/' + commercialLicense.parentFile.name + '/license.txt'
                        }.singleFile
                        if (commercialLicense.length() != patchedLicense.length()) {
                            throw new GradleException("License files didn't match for " + commercialLicense.parentFile.name)
                        }
                    }
                }
            }
        }
    }
}
