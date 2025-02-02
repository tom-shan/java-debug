/*******************************************************************************
* Copyright (c) 2019 Microsoft Corporation and others.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v1.0
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html
*
* Contributors:
*     Microsoft Corporation - initial API and implementation
*******************************************************************************/

package com.microsoft.java.debug.core.adapter.handler;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.microsoft.java.debug.core.Configuration;
import com.microsoft.java.debug.core.adapter.IDebugAdapterContext;
import com.microsoft.java.debug.core.adapter.IDebugRequestHandler;
import com.microsoft.java.debug.core.adapter.LaunchMode;
import com.microsoft.java.debug.core.protocol.Messages.Response;
import com.microsoft.java.debug.core.protocol.Requests.Arguments;
import com.microsoft.java.debug.core.protocol.Requests.Command;

public abstract class AbstractDisconnectRequestHandler implements IDebugRequestHandler {
    private static final Logger logger = Logger.getLogger(Configuration.LOGGER_NAME);

    @Override
    public List<Command> getTargetCommands() {
        return Arrays.asList(Command.DISCONNECT);
    }

    @Override
    public CompletableFuture<Response> handle(Command command, Arguments arguments, Response response,
            IDebugAdapterContext context) {
        destroyDebugSession(command, arguments, response, context);
        destroyResource(context);
        return CompletableFuture.completedFuture(response);
    }

    /**
     * Destroy the resources generated by the debug session.
     *
     * @param context the debug context
     */
    private void destroyResource(IDebugAdapterContext context) {
        if (shouldDestroyLaunchFiles(context)) {
            destroyLaunchFiles(context);
        }
    }

    private boolean shouldDestroyLaunchFiles(IDebugAdapterContext context) {
        // Delete the temporary launch files must happen after the debuggee process is fully exited,
        // otherwise it throws error saying the file is being used by other process.
        // In Debug mode, the debugger is able to receive VM terminate event. It's sensible to do cleanup.
        // In noDebug mode, if the debuggee is launched internally by the debugger, the debugger knows
        // when the debuggee process exited. Should do cleanup. But if the debuggee is launched in the
        // integrated/external terminal, the debugger lost the contact with the debuggee after it's launched.
        // Have no idea when the debuggee is exited. So ignore the cleanup.
        return context.getLaunchMode() == LaunchMode.DEBUG || context.getDebuggeeProcess() != null;
    }

    private void destroyLaunchFiles(IDebugAdapterContext context) {
        // Sometimes when the debug session is terminated, the debuggee process is not exited immediately.
        // Add retry to delete the temporary launch files.
        int retry = 5;
        while (retry-- > 0) {
            try {
                if (context.getClasspathJar() != null) {
                    Files.deleteIfExists(context.getClasspathJar());
                    context.setClasspathJar(null);
                }

                if (context.getArgsfile() != null) {
                    Files.deleteIfExists(context.getArgsfile());
                    context.setArgsfile(null);
                }
            } catch (IOException e) {
                // do nothing.
                logger.log(Level.WARNING, "Failed to destory launch files, will retry again.");
            }

            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {
                // do nothing.
            }
        }
    }

    protected abstract void destroyDebugSession(Command command, Arguments arguments, Response response, IDebugAdapterContext context);
}
