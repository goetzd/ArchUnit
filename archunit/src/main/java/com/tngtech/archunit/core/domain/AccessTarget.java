/*
 * Copyright 2018 TNG Technology Consulting GmbH
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
package com.tngtech.archunit.core.domain;

import java.lang.annotation.Annotation;
import java.util.Objects;
import java.util.Set;

import com.google.common.base.Predicate;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.tngtech.archunit.PublicAPI;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.base.Optional;
import com.tngtech.archunit.core.domain.properties.CanBeAnnotated;
import com.tngtech.archunit.core.domain.properties.HasName;
import com.tngtech.archunit.core.domain.properties.HasOwner;
import com.tngtech.archunit.core.domain.properties.HasOwner.Functions.Get;
import com.tngtech.archunit.core.domain.properties.HasParameterTypes;
import com.tngtech.archunit.core.domain.properties.HasReturnType;
import com.tngtech.archunit.core.importer.DomainBuilders.CodeUnitCallTargetBuilder;
import com.tngtech.archunit.core.importer.DomainBuilders.ConstructorCallTargetBuilder;
import com.tngtech.archunit.core.importer.DomainBuilders.FieldAccessTargetBuilder;
import com.tngtech.archunit.core.importer.DomainBuilders.MethodCallTargetBuilder;

import static com.tngtech.archunit.PublicAPI.Usage.ACCESS;
import static com.tngtech.archunit.base.DescribedPredicate.equalTo;
import static com.tngtech.archunit.core.domain.JavaConstructor.CONSTRUCTOR_NAME;
import static com.tngtech.archunit.core.domain.properties.HasName.Functions.GET_NAME;

public abstract class AccessTarget implements HasName.AndFullName, CanBeAnnotated, HasOwner<JavaClass> {
    private final String name;
    private final JavaClass owner;
    private final String fullName;

    AccessTarget(JavaClass owner, String name, String fullName) {
        this.name = name;
        this.owner = owner;
        this.fullName = fullName;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public JavaClass getOwner() {
        return owner;
    }

    @Override
    public String getFullName() {
        return fullName;
    }

    @Override
    public int hashCode() {
        return Objects.hash(fullName);
    }

    /**
     * Tries to resolve the targeted members (methods, fields or constructors). In most cases this will be a
     * single element, if the target was imported, or an empty set, if the target was not imported. However,
     * for {@link MethodCallTarget MethodCallTargets}, there can be multiple possible targets.
     *
     * @see MethodCallTarget#resolve()
     * @see FieldAccessTarget#resolve()
     * @see ConstructorCallTarget#resolve()
     *
     * @return Set of all members that match the call target
     */
    @PublicAPI(usage = ACCESS)
    public abstract Set<? extends JavaMember> resolve();

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final AccessTarget other = (AccessTarget) obj;
        return Objects.equals(this.fullName, other.fullName);
    }

    @Override
    public String toString() {
        return "target{" + fullName + '}';
    }

    /**
     * Returns true, if one of the resolved targets is annotated with the given annotation type.<br>
     * NOTE: If the target was not imported, this method will always return false.
     *
     * @param annotationType The type of the annotation to check for
     * @return true if one of the resolved targets is annotated with the given type
     */
    @Override
    public boolean isAnnotatedWith(Class<? extends Annotation> annotationType) {
        return isAnnotatedWith(annotationType.getName());
    }

    /**
     * @see AccessTarget#isAnnotatedWith(Class)
     */
    @Override
    public boolean isAnnotatedWith(final String annotationTypeName) {
        return anyMember(new Predicate<JavaMember>() {
            @Override
            public boolean apply(JavaMember input) {
                return input.isAnnotatedWith(annotationTypeName);
            }
        });
    }

    /**
     * Returns true, if one of the resolved targets is annotated with an annotation matching the predicate.<br>
     * NOTE: If the target was not imported, this method will always return false.
     *
     * @param predicate Qualifies matching annotations
     * @return true if one of the resolved targets is annotated with an annotation matching the predicate
     */
    @Override
    public boolean isAnnotatedWith(final DescribedPredicate<? super JavaAnnotation> predicate) {
        return anyMember(new Predicate<JavaMember>() {
            @Override
            public boolean apply(JavaMember input) {
                return input.isAnnotatedWith(predicate);
            }
        });
    }

    @Override
    public boolean isMetaAnnotatedWith(Class<? extends Annotation> annotationType) {
        return isMetaAnnotatedWith(annotationType.getName());
    }

    @Override
    public boolean isMetaAnnotatedWith(final String annotationTypeName) {
        return anyMember(new Predicate<JavaMember>() {
            @Override
            public boolean apply(JavaMember input) {
                return input.isMetaAnnotatedWith(annotationTypeName);
            }
        });
    }

    @Override
    public boolean isMetaAnnotatedWith(final DescribedPredicate<? super JavaAnnotation> predicate) {
        return anyMember(new Predicate<JavaMember>() {
            @Override
            public boolean apply(JavaMember input) {
                return input.isMetaAnnotatedWith(predicate);
            }
        });
    }

    private boolean anyMember(Predicate<JavaMember> predicate) {
        for (final JavaMember member : resolve()) {
            if (predicate.apply(member)) {
                return true;
            }
        }
        return false;
    }

    abstract String getDescription();

    // NOTE: JDK 1.7 u80 seems to have a bug here, if we import HasType, the compile will fail???
    public static final class FieldAccessTarget extends AccessTarget implements com.tngtech.archunit.core.domain.properties.HasType {
        private final JavaClass type;
        private final Supplier<Optional<JavaField>> field;

        FieldAccessTarget(FieldAccessTargetBuilder builder) {
            super(builder.getOwner(), builder.getName(), builder.getFullName());
            this.type = builder.getType();
            this.field = Suppliers.memoize(builder.getField());
        }

        @Override
        public JavaClass getType() {
            return type;
        }

        /**
         * @return A field that matches this target, or {@link Optional#absent()} if no matching field was imported.
         */
        @PublicAPI(usage = ACCESS)
        public Optional<JavaField> resolveField() {
            return field.get();
        }

        /**
         * @return Fields that match the target, this will always be either one field, or no field
         * @see #resolveField()
         */
        @Override
        public Set<JavaField> resolve() {
            return resolveField().asSet();
        }

        @Override
        String getDescription() {
            return "field <" + getFullName() + ">";
        }
    }

    public abstract static class CodeUnitCallTarget extends AccessTarget implements HasParameterTypes, HasReturnType {
        private final ImmutableList<JavaClass> parameters;
        private final JavaClass returnType;

        CodeUnitCallTarget(CodeUnitCallTargetBuilder<?> builder) {
            super(builder.getOwner(), builder.getName(), builder.getFullName());
            this.parameters = ImmutableList.copyOf(builder.getParameters());
            this.returnType = builder.getReturnType();
        }

        @Override
        public JavaClassList getParameters() {
            return DomainObjectCreationContext.createJavaClassList(parameters);
        }

        @Override
        public JavaClass getReturnType() {
            return returnType;
        }

        /**
         * Tries to resolve the targeted method or constructor.
         *
         * @see ConstructorCallTarget#resolveConstructor()
         * @see MethodCallTarget#resolve()
         */
        @Override
        public abstract Set<? extends JavaCodeUnit> resolve();
    }

    public static final class ConstructorCallTarget extends CodeUnitCallTarget {
        private final Supplier<Optional<JavaConstructor>> constructor;

        ConstructorCallTarget(ConstructorCallTargetBuilder builder) {
            super(builder);
            constructor = builder.getConstructor();
        }

        /**
         * @return A constructor that matches this target, or {@link Optional#absent()} if no matching constructor
         * was imported.
         */
        @PublicAPI(usage = ACCESS)
        public Optional<JavaConstructor> resolveConstructor() {
            return constructor.get();
        }

        /**
         * @return constructors that match the target, this will always be either one constructor, or no constructor
         * @see #resolveConstructor()
         */
        @Override
        public Set<JavaConstructor> resolve() {
            return resolveConstructor().asSet();
        }

        @Override
        String getDescription() {
            return "constructor <" + getFullName() + ">";
        }
    }

    public static final class MethodCallTarget extends CodeUnitCallTarget {
        private final Supplier<Set<JavaMethod>> methods;

        MethodCallTarget(MethodCallTargetBuilder builder) {
            super(builder);
            this.methods = Suppliers.memoize(builder.getMethods());
        }

        /**
         * Attempts to resolve imported methods that match this target. Note that while usually there is one unique
         * target (if imported), it is possible that the call is ambiguous. For example consider
         * <pre><code>
         * interface A {
         *     void foo();
         * }
         *
         * interface B {
         *     void foo();
         * }
         *
         * interface D extends A, B {}
         *
         * class X {
         *     D d;
         *     // ...
         *     void bar() {
         *         d.foo();
         *     }
         * }
         * </code></pre>
         * While, for any concrete implementation, the compiler will naturally resolve one concrete target to link to,
         * and thus at runtime the called target ist clear, from an analytical point of view the relevant target
         * can't be uniquely identified here. To sum up, the result can be
         * <ul>
         * <li>empty - if no imported method matches the target</li>
         * <li>a single method - if the method was imported and can uniquely be identified</li>
         * <li>several methods - in scenarios where there is no unique method that matches the target</li>
         * </ul>
         * Note that the target would be uniquely determinable, if D would declare <code>void foo()</code> itself.
         *
         * @return Set of matching methods, usually a single target
         */
        @Override
        public Set<JavaMethod> resolve() {
            return methods.get();
        }

        @Override
        String getDescription() {
            return "method <" + getFullName() + ">";
        }
    }

    public static final class Predicates {
        private Predicates() {
        }

        @PublicAPI(usage = ACCESS)
        public static DescribedPredicate<AccessTarget> declaredIn(Class<?> clazz) {
            return declaredIn(clazz.getName());
        }

        @PublicAPI(usage = ACCESS)
        public static DescribedPredicate<AccessTarget> declaredIn(String className) {
            return declaredIn(GET_NAME.is(equalTo(className)).as(className));
        }

        @PublicAPI(usage = ACCESS)
        public static DescribedPredicate<AccessTarget> declaredIn(DescribedPredicate<? super JavaClass> predicate) {
            return Get.<JavaClass>owner().is(predicate)
                    .as("declared in %s", predicate.getDescription())
                    .forSubType();
        }

        @PublicAPI(usage = ACCESS)
        public static DescribedPredicate<AccessTarget> constructor() {
            return new DescribedPredicate<AccessTarget>("constructor") {
                @Override
                public boolean apply(AccessTarget input) {
                    return CONSTRUCTOR_NAME.equals(input.getName()); // The constructor name is sufficiently unique
                }
            };
        }
    }
}
