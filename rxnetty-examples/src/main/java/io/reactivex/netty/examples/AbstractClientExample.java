/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.reactivex.netty.examples;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.NOPLogger;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

public class AbstractClientExample {

    protected static Logger logger = LoggerFactory.getLogger(AbstractClientExample.class);

    public static void mockLogger(Logger logger) {
        AbstractClientExample.logger = logger;
    }

    public static SocketAddress getServerAddress(Class<? extends AbstractServerExample> serverClass, String[] args) {

        String host = "127.0.0.1";

        if(null != args && args.length > 0) {
            String portAsStr = args[0];
            try {
                int port = Integer.parseInt(portAsStr);
                if (args.length > 1) {
                    host = args[1];
                }

                return new InetSocketAddress(host, port);
            } catch (NumberFormatException e) {
                printUsageAndExit();
            }
        }

        boolean started = isStarted(serverClass);

        if (started) {
            _getServerPort(serverClass);
        } else {
            try {
                disableServerLogger(serverClass);
                Method method = serverClass.getMethod("main", String[].class);
                String[] params = { "false" };
                method.invoke(null, (Object) params);
                if (isStarted(serverClass)) {
                    int serverPort = _getServerPort(serverClass);
                    if (AbstractServerExample.NOT_STARTED_PORT == serverPort) {
                        throw new RuntimeException("Failed to start the server: " + serverClass.getName());
                    } else {
                        return new InetSocketAddress(host, serverPort);
                    }
                }
            } catch (IllegalAccessException e) {
                System.err.println("Failed to invoke main method on the server.");
                e.printStackTrace();
            } catch (NoSuchMethodException e) {
                System.err.println("Failed to get the main method on the server. ");
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                System.err.println("Failed to invoke main method on the server. ");
                e.printStackTrace();
            }
        }

        throw new RuntimeException("Failed to start the server: " + serverClass.getName());
    }

    private static void printUsageAndExit() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        for (int i = stackTrace.length - 1; i >= 0; i--) {
            StackTraceElement stackTraceElement = stackTrace[i];
            try {
                final String className = stackTraceElement.getClassName();
                if (AbstractClientExample.class.isAssignableFrom(Class.forName(className))) {
                    System.out.println("Usage: java " + className + " [<server port> [<server host>]]");
                    System.exit(-1);
                }
            } catch (ClassNotFoundException e) {
                // Ignore...
            }
        }
        System.exit(-1);
    }

    private static boolean isStarted(Class<? extends AbstractServerExample> serverClass) {
        try {
            Method method = serverClass.getMethod("isServerStarted", Class.class);
            return  (boolean) method.invoke(null, serverClass);
        } catch (IllegalAccessException e) {
            System.err.println("Failed to invoke isServerStarted method on the server.");
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            System.err.println("Failed to get the isServerStarted method on the server. ");
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            System.err.println("Failed to invoke isServerStarted method on the server. ");
            e.printStackTrace();
        }
        return false;
    }

    private static int _getServerPort(Class<? extends AbstractServerExample> serverClass) {
        try {
            Method method = serverClass.getMethod("getServerPort", Class.class);
            return (int) method.invoke(null, serverClass);
        } catch (IllegalAccessException e) {
            System.err.println("Failed to invoke getServerPort method on the server.");
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            System.err.println("Failed to get the getServerPort method on the server. ");
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            System.err.println("Failed to invoke getServerPort method on the server. ");
            e.printStackTrace();
        }

        return AbstractServerExample.NOT_STARTED_PORT;
    }

    private static void disableServerLogger(Class<? extends AbstractServerExample> serverClass)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {

        final Method mockLoggerMethod = serverClass.getMethod("mockLogger", Logger.class);
        mockLoggerMethod.invoke(null, NOPLogger.NOP_LOGGER);
    }
}
