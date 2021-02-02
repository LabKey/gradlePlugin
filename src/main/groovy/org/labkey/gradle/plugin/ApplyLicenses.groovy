package org.labkey.gradle.plugin;

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.bundling.Jar
import org.labkey.gradle.util.BuildUtils
import org.labkey.gradle.util.GroupNames
import org.gradle.api.file.DuplicatesStrategy

class ApplyLicenses implements Plugin<Project>
{
    @Override
    public void apply(Project project)
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
            extJsCommercial
            licensePatch {
                canBeConsumed = true
                canBeResolved = true
            }
        }
        project.configurations.extJsCommercial.setDescription("extJs commercial license libraries")
        project.configurations.licensePatch.setDescription("Modules that require patching with commercial-license libraries")
    }


    private void addDependencies(Project project)
    {
        if (!BuildUtils.isOpenSource(project)) {
            project.dependencies {
                extJsCommercial "com.sencha.extjs:extjs:4.2.1:commercial@zip"
                extJsCommercial "com.sencha.extjs:extjs:3.4.1:commercial@zip"
            }

            BuildUtils.addLabKeyDependency(project, "licensePatch", BuildUtils.getApiProjectPath(project.gradle), "published", project.getVersion().toString(), "module")
        }
    }

    private static void addTasks(Project project)
    {
        if (!BuildUtils.isOpenSource(project)) {
            project.tasks.register('patchApiModule', Jar) {
                Jar jar ->
                    jar.group = GroupNames.DISTRIBUTION
                    jar.description = "Patches the api module to replace ExtJS libraries with commercial versions"
                    jar.archiveBaseName.set("api")
                    jar.archiveVersion.set(project.getVersion().toString())
                    jar.archiveClassifier.set("extJsCommercial")
                    jar.archiveExtension.set('module')
                    jar.destinationDirectory = project.file("${project.buildDir}/patchApiModule")
                    jar.outputs.cacheIf({ true })
                    // first include the ext-3.4.1 and ext-4.2.1 directories from the extjs configuration artifacts
                    jar.into('web') {
                        from project.configurations.extJsCommercial.collect {
                            project.zipTree(it)
                        }
                    }
                    // include the original module file ...
                    jar.from project.configurations.licensePatch.collect {
                        project.zipTree(it)
                    }
                    // ... but don't use the ext directories that come from that file
                    jar.setDuplicatesStrategy(DuplicatesStrategy.EXCLUDE)
                    jar.manifest.attributes(
                            "Implementation-Version": project.version,
                            "Implementation-Title": "Internal API classes",
                            "Implementation-Vendor": "LabKey"
                    )
            }
        }
    }
}
