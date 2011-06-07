/*
 * Copyright 2011 JBoss, a divison Red Hat, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.errai.ioc.rebind.ioc.codegen.builder.impl;


import com.google.gwt.core.ext.typeinfo.JClassType;
import org.jboss.errai.ioc.rebind.ioc.codegen.*;
import org.jboss.errai.ioc.rebind.ioc.codegen.builder.ArrayBuilder;
import org.jboss.errai.ioc.rebind.ioc.codegen.builder.BuildCallback;
import org.jboss.errai.ioc.rebind.ioc.codegen.exception.UndefinedConstructorException;
import org.jboss.errai.ioc.rebind.ioc.codegen.meta.MetaClass;
import org.jboss.errai.ioc.rebind.ioc.codegen.meta.MetaField;

/**
 * @author Mike Brock <cbrock@redhat.com>
 * @author Christian Sadilek <csadilek@redhat.com>
 */
public class ObjectBuilder extends AbstractStatementBuilder implements ArrayBuilder {
    StringBuilder buf = new StringBuilder();

    private static final int CONSTRUCT_STATEMENT_COMPLETE = 1;
    private static final int SUBCLASSED = 2;
    private static final int FINISHED = 3;

    private MetaClass type;
    private int buildState;
    private Integer length;

    ObjectBuilder(MetaClass type) {
        super(Context.create());
        this.type = type;

        for (MetaField field : type.getFields()) {
            context.addVariable(Variable.create(field.getName(), field.getType()));
        }
    }

    public static ObjectBuilder newInstanceOf(MetaClass type) {
        return new ObjectBuilder(type).newInstance();
    }

    public static ObjectBuilder newInstanceOf(Class type) {
        return newInstanceOf(MetaClassFactory.get(type));
    }

    public static ObjectBuilder newInstanceOf(JClassType type) {
        return newInstanceOf(MetaClassFactory.get(type));
    }
    
    public static ArrayBuilder newArrayOf(MetaClass type) {
        return new ObjectBuilder(type).newArrayInstance(null);
    }

    public static ArrayBuilder newArrayOf(Class type) {
        return newArrayOf(MetaClassFactory.get(type));
    }

    public static ArrayBuilder newArrayOf(JClassType type) {
        return newArrayOf(MetaClassFactory.get(type));
    }
    
    public static ArrayBuilder newArrayOf(MetaClass type, int length) {
        return new ObjectBuilder(type).newArrayInstance(length);
    }

    public static ArrayBuilder newArrayOf(Class type, int length) {
        return newArrayOf(MetaClassFactory.get(type), length);
    }

    public static ArrayBuilder newArrayOf(JClassType type, int length) {
        return newArrayOf(MetaClassFactory.get(type), length);
    }

    private ArrayBuilder newArrayInstance(Integer length) {
        this.length = length;
        
        buf.append("new ").append(type.getFullyQualifedName())
           .append("[").append((length==null)?"":length).append("]");
        buildState = FINISHED;
        return this;
    }
    
    public AbstractStatementBuilder initialize(Object... values) {
        if (length!=null && values.length > length) throw new RuntimeException("Too many values");
        
        buf.append(" {\n");
        for (int i=0; i<values.length; i++) {
            Statement s = GenUtil.generate(context, values[i]);
            GenUtil.assertAssignableTypes(s.getType(), type);
            buf.append(s.generate(context));
            if (i + 1 < values.length) {
                buf.append(", ");
            }
        }
        buf.append("\n}");
        return this;
    }
    
    private ObjectBuilder newInstance() {
        buf.append("new ").append(type.getFullyQualifedName());
        return this;
    }

    public ObjectBuilder withParameters(Object... parameters) {
        return withParameters(GenUtil.generateCallParameters(getContext(), parameters));
    }

    public ObjectBuilder withParameters(Statement... parameters) {
        return withParameters(CallParameters.fromStatements(parameters));
    }

    public ObjectBuilder withParameters(CallParameters parameters) {
        if (!type.isInterface() && type.getBestMatchingConstructor(parameters.getParameterTypes()) == null)
            throw new UndefinedConstructorException(type, parameters.getParameterTypes());

        buf.append(parameters.generate(Context.create()));
        buildState |= CONSTRUCT_STATEMENT_COMPLETE;
        return this;
    }

    public ClassStructureBuilder extend() {
        return new ClassStructureBuilder(type, new BuildCallback<ObjectBuilder>() {
            public ObjectBuilder callback(Statement statement) {
                finishConstructIfNecessary();
                buf.append(" {\n").append(statement.generate(context)).append("\n}\n");
                return ObjectBuilder.this;
            }
        });
    }

    private void finishConstructIfNecessary() {
        if ((buildState & CONSTRUCT_STATEMENT_COMPLETE) == 0) {
            withParameters(CallParameters.none());
        }
    }

    public MetaClass getType() {
        return type;
    }

    public String generate(Context context) {
        finishConstructIfNecessary();
        return buf.toString();
    }
}
