/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

/*
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 */

package org.opensearch.bootstrap;

import org.opensearch.common.SuppressForbidden;

import java.io.FilePermission;
import java.io.IOException;
import java.net.SocketPermission;
import java.net.URL;
import java.security.CodeSource;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.security.Policy;
import java.security.ProtectionDomain;
import java.util.Collections;
import java.util.Map;
import java.util.function.Predicate;

/** custom policy for union of static and dynamic permissions */
final class OpenSearchPolicy extends Policy {

    /** template policy file, the one used in tests */
    static final String POLICY_RESOURCE = "security.policy";
    /** limited policy for scripts */
    static final String UNTRUSTED_RESOURCE = "untrusted.policy";

    final Policy template;
    final Policy untrusted;
    final Policy system;
    final PermissionCollection dynamic;
    final PermissionCollection dataPathPermission;
    final Map<String,Policy> plugins;

    OpenSearchPolicy(Map<String, URL> codebases, PermissionCollection dynamic, Map<String,Policy> plugins, boolean filterBadDefaults,
                     PermissionCollection dataPathPermission) {
        this.template = Security.readPolicy(getClass().getResource(POLICY_RESOURCE), codebases);
        this.dataPathPermission = dataPathPermission;
        this.untrusted = Security.readPolicy(getClass().getResource(UNTRUSTED_RESOURCE), Collections.emptyMap());
        if (filterBadDefaults) {
            this.system = new SystemPolicy(Policy.getPolicy());
        } else {
            this.system = Policy.getPolicy();
        }
        this.dynamic = dynamic;
        this.plugins = plugins;
    }

    @Override @SuppressForbidden(reason = "fast equals check is desired")
    public boolean implies(ProtectionDomain domain, Permission permission) {
        CodeSource codeSource = domain.getCodeSource();
        // codesource can be null when reducing privileges via doPrivileged()
        if (codeSource == null) {
            return false;
        }

        URL location = codeSource.getLocation();
        // location can be null... ??? nobody knows
        // https://bugs.openjdk.java.net/browse/JDK-8129972
        if (location != null) {
            // run scripts with limited permissions
            if (BootstrapInfo.UNTRUSTED_CODEBASE.equals(location.getFile())) {
                return untrusted.implies(domain, permission);
            }
            // check for an additional plugin permission: plugin policy is
            // only consulted for its codesources.
            Policy plugin = plugins.get(location.getFile());
            if (plugin != null && plugin.implies(domain, permission)) {
                return true;
            }
        }

        // Special handling for broken Hadoop code: "let me execute or my classes will not load"
        // yeah right, REMOVE THIS when hadoop is fixed
        if (permission instanceof FilePermission && "<<ALL FILES>>".equals(permission.getName())) {
            for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
                if ("org.apache.hadoop.util.Shell".equals(element.getClassName()) &&
                      "runCommand".equals(element.getMethodName())) {
                    // we found the horrible method: the hack begins!
                    // force the hadoop code to back down, by throwing an exception that it catches.
                    rethrow(new IOException("no hadoop, you cannot do this."));
                }
            }
        }

        // The FilePermission to check access to the path.data is the hottest permission check in
        // OpenSearch, so we check it first.
        if (permission instanceof FilePermission && dataPathPermission.implies(permission)) {
            return true;
        }

        // otherwise defer to template + dynamic file permissions
        return template.implies(domain, permission) || dynamic.implies(permission) || system.implies(domain, permission);
    }

    /**
     * Classy puzzler to rethrow any checked exception as an unchecked one.
     */
    private static class Rethrower<T extends Throwable> {
        private void rethrow(Throwable t) throws T {
            throw (T) t;
        }
    }

    /**
     * Rethrows <code>t</code> (identical object).
     */
    private void rethrow(Throwable t) {
        new Rethrower<Error>().rethrow(t);
    }

    @Override
    public PermissionCollection getPermissions(CodeSource codesource) {
        // code should not rely on this method, or at least use it correctly:
        // https://bugs.openjdk.java.net/browse/JDK-8014008
        // return them a new empty permissions object so jvisualvm etc work
        for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
            if ("sun.rmi.server.LoaderHandler".equals(element.getClassName()) &&
                    "loadClass".equals(element.getMethodName())) {
                return new Permissions();
            }
        }
        // return UNSUPPORTED_EMPTY_COLLECTION since it is safe.
        return super.getPermissions(codesource);
    }

    // TODO: remove this hack when insecure defaults are removed from java

    /**
     * Wraps a bad default permission, applying a pre-implies to any permissions before checking if the wrapped bad default permission
     * implies a permission.
     */
    private static class BadDefaultPermission extends Permission {

        private final Permission badDefaultPermission;
        private final Predicate<Permission> preImplies;

        /**
         * Construct an instance with a pre-implies check to apply to desired permissions.
         *
         * @param badDefaultPermission the bad default permission to wrap
         * @param preImplies           a test that is applied to a desired permission before checking if the bad default permission that
         *                             this instance wraps implies the desired permission
         */
        BadDefaultPermission(final Permission badDefaultPermission, final Predicate<Permission> preImplies) {
            super(badDefaultPermission.getName());
            this.badDefaultPermission = badDefaultPermission;
            this.preImplies = preImplies;
        }

        @Override
        public final boolean implies(Permission permission) {
            return preImplies.test(permission) && badDefaultPermission.implies(permission);
        }

        @Override
        public final boolean equals(Object obj) {
            return badDefaultPermission.equals(obj);
        }

        @Override
        public int hashCode() {
            return badDefaultPermission.hashCode();
        }

        @Override
        public String getActions() {
            return badDefaultPermission.getActions();
        }

    }

    // default policy file states:
    // "It is strongly recommended that you either remove this permission
    //  from this policy file or further restrict it to code sources
    //  that you specify, because Thread.stop() is potentially unsafe."
    // not even sure this method still works...
    private static final Permission BAD_DEFAULT_NUMBER_ONE = new BadDefaultPermission(new RuntimePermission("stopThread"), p -> true);

    // default policy file states:
    // "allows anyone to listen on dynamic ports"
    // specified exactly because that is what we want, and fastest since it won't imply any
    // expensive checks for the implicit "resolve"
    private static final Permission BAD_DEFAULT_NUMBER_TWO =
        new BadDefaultPermission(
            new SocketPermission("localhost:0", "listen"),
            // we apply this pre-implies test because some SocketPermission#implies calls do expensive reverse-DNS resolves
            p -> p instanceof SocketPermission && p.getActions().contains("listen"));

    /**
     * Wraps the Java system policy, filtering out bad default permissions that
     * are granted to all domains. Note, before java 8 these were even worse.
     */
    static class SystemPolicy extends Policy {
        final Policy delegate;

        SystemPolicy(Policy delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean implies(ProtectionDomain domain, Permission permission) {
            if (BAD_DEFAULT_NUMBER_ONE.implies(permission) || BAD_DEFAULT_NUMBER_TWO.implies(permission)) {
                return false;
            }
            return delegate.implies(domain, permission);
        }
    }
}
