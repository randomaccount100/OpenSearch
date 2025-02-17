/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/*
 * Copyright (C) 2008 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 */

package org.opensearch.common.inject.spi;

import org.opensearch.common.inject.Binder;
import org.opensearch.common.inject.Key;
import org.opensearch.common.inject.Provider;

import java.util.Objects;

/**
 * A lookup of the provider for a type. Lookups are created explicitly in a module using
 * {@link org.opensearch.common.inject.Binder#getProvider(Class) getProvider()} statements:
 * <pre>
 *     Provider&lt;PaymentService&gt; paymentServiceProvider
 *         = getProvider(PaymentService.class);</pre>
 *
 * @author jessewilson@google.com (Jesse Wilson)
 * @since 2.0
 */
public final class ProviderLookup<T> implements Element {

    // NOTE: this class is not part of guice and was added so the provider lookup's key can be accessible for tests
    public static class ProviderImpl<T> implements Provider<T> {
        private ProviderLookup<T> lookup;

        private ProviderImpl(ProviderLookup<T> lookup) {
            this.lookup = lookup;
        }

        @Override
        public T get() {
            if (lookup.delegate == null) {
                throw new IllegalStateException( "This Provider cannot be used until the Injector has been created.");
            }
            return lookup.delegate.get();
        }

        @Override
        public String toString() {
            return "Provider<" + lookup.key.getTypeLiteral() + ">";
        }

        public Key<T> getKey() {
            return lookup.getKey();
        }
    }
    private final Object source;
    private final Key<T> key;
    private Provider<T> delegate;

    public ProviderLookup(Object source, Key<T> key) {
        this.source = Objects.requireNonNull(source, "source");
        this.key = Objects.requireNonNull(key, "key");
    }

    @Override
    public Object getSource() {
        return source;
    }

    public Key<T> getKey() {
        return key;
    }

    @Override
    public <T> T acceptVisitor(ElementVisitor<T> visitor) {
        return visitor.visit(this);
    }

    /**
     * Sets the actual provider.
     *
     * @throws IllegalStateException if the delegate is already set
     */
    public void initializeDelegate(Provider<T> delegate) {
        if (this.delegate != null) {
            throw new IllegalStateException("delegate already initialized");
        }
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public void applyTo(Binder binder) {
        initializeDelegate(binder.withSource(getSource()).getProvider(key));
    }

    /**
     * Returns the delegate provider, or {@code null} if it has not yet been initialized. The delegate
     * will be initialized when this element is processed, or otherwise used to create an injector.
     */
    public Provider<T> getDelegate() {
        return delegate;
    }

    /**
     * Returns the looked up provider. The result is not valid until this lookup has been initialized,
     * which usually happens when the injector is created. The provider will throw an {@code
     * IllegalStateException} if you try to use it beforehand.
     */
    public Provider<T> getProvider() {
        return new ProviderImpl<>(this);
    }
}
