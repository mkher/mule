/*
 * $Id$
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.test.transformers;

import org.mule.DefaultMuleMessage;
import org.mule.api.MuleMessage;
import org.mule.api.transformer.TransformerException;
import org.mule.expression.ExpressionConfig;
import org.mule.expression.transformers.ExpressionArgument;
import org.mule.expression.transformers.ExpressionTransformer;
import org.mule.tck.junit4.AbstractMuleContextTestCase;

import groovyjarjarasm.asm.ClassWriter;
import groovyjarjarasm.asm.Opcodes;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ExpressionTransformerTestCase extends AbstractMuleContextTestCase
{

    /**
     * See: MULE-4797 GroovyExpressionEvaluator script is unable to load user classes
     * when used with hot deployment.
     * 
     * @throws TransformerException
     */
    @Test
    public void testExpressionEvaluationClassLoader() throws ClassNotFoundException, TransformerException
    {
        ExpressionTransformer transformer = new ExpressionTransformer();
        transformer.setMuleContext(muleContext);
        transformer.addArgument(new ExpressionArgument("test", new ExpressionConfig(
            "payload instanceof MyClass", "groovy", null), false));

        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        try
        {
            Thread.currentThread().setContextClassLoader(new MyClassClassLoader());
            transformer.initialise();
        }
        catch (Exception e)
        {
            fail(e.getMessage());
        }
        finally
        {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }

        assertFalse((Boolean) transformer.transform("test"));
    }

    @Test
    public void testNullPayloadIsConsideredAsNullResult() throws Exception
    {
        ExpressionTransformer transformer = new ExpressionTransformer();
        transformer.setMuleContext(muleContext);
        transformer.setReturnSourceIfNull(true);
        ExpressionConfig config = new ExpressionConfig("null", "groovy", null);
        ExpressionArgument argument = new ExpressionArgument("test", config, false);
        argument.setMuleContext(muleContext);
        transformer.addArgument(argument);

        MuleMessage message = new DefaultMuleMessage("Test", muleContext);
        Object result = transformer.transformMessage(message, null);
        assertTrue(result instanceof MuleMessage);
        MuleMessage transformedMessage = (MuleMessage) result;

        assertEquals("Test", transformedMessage.getPayload());

    }

    class MyClassClassLoader extends ClassLoader
    {
        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException
        {
            if (name.equals("MyClass"))
            {
                ClassWriter cw = new ClassWriter(true);
                cw.visit(Opcodes.V1_5, Opcodes.ACC_PUBLIC, "MyClass", null, "java/lang/Object", null);
                return defineClass(name, cw.toByteArray(), 0, cw.toByteArray().length);
            }
            else
            {
                return super.findClass(name);
            }
        }
    }
}
