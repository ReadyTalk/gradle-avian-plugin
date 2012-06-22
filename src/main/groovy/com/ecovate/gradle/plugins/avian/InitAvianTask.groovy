package com.ecovate.gradle.plugins.avian

import java.io.File
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction


class InitAvianTask extends AvianTask {

	@Override
	protected void addDependencies() {
		project.dependencies {
			if(platform.equals('darwin')) {
				compile "com.ecovate.avian-darwin-x86_64:avian-$platform-$arch:$avianVersion@tar.gz"
			} else {
				compile "com.ecovate.avian-linux-x86_64:avian-$platform-$arch:$avianVersion@tar.gz"
			}
		}
	}

	@TaskAction
	protected void run() {
		stage1.mkdirs()
		if( ! project.hasProperty('avian-location')) {
			project.copy {
				from tree(project.configurations.compile.findAll{it.name.contains('avian') && it.name.endsWith('.tar.gz')}.first())
				into project.file('build/avian')
			}
		}
		runCommand(['cp', "$avian/classpath.jar", "$stage1/boot.jar"])

		project.compileJava.options.define(bootClasspath: "$stage1/boot.jar".toString())
	}

}
