package com.ecovate.gradle.plugins.avian

import java.io.File
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction


class InitVersionTask extends AvianTask {

	@Override
	protected void addDependencies() {
		// nothing to do
	}

	@TaskAction
	protected void run() {
		if(mainClass != null) {
			project.sourceSets.main.java.srcDirs.each {
				File main = new File(it, mainClass.replace('.','/') + '.java')
				if(main.isFile()) {
					main.write(
						main.text.replaceAll('(\\s+String\\s+APP_VERSION\\s*=\\s*")[\\d\\.v]+("\\s*;)', "\$1$version\$2")
					)
					return;
				}
			}
		}
	}

}
