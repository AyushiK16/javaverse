
package com.matrix.tracer;

import com.sun.jdi.Bootstrap;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.LaunchingConnector;

import java.nio.file.Path;
import java.util.Map;

public class TargetLauncher {

    public VirtualMachine launch(Path classesDirectory, String className)
            throws Exception {

        LaunchingConnector connector =
                Bootstrap.virtualMachineManager().defaultConnector();

        Map<String, Connector.Argument> arguments =
                connector.defaultArguments();

        arguments.get("main").setValue(className);

        arguments.get("options").setValue(
                "-cp \"" + classesDirectory.toAbsolutePath() + "\""
        );

        return connector.launch(arguments);
    }
}
