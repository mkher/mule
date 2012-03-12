/*
 * $Id$
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.el.mvel;

import org.mule.api.MuleContext;
import org.mule.api.MuleEvent;
import org.mule.api.MuleMessage;
import org.mule.api.el.ExpressionLanguage;
import org.mule.api.el.ExpressionLanguageContext;
import org.mule.api.el.ExpressionLanguageExtension;
import org.mule.api.expression.ExpressionRuntimeException;
import org.mule.api.expression.InvalidExpressionException;
import org.mule.api.lifecycle.Initialisable;
import org.mule.api.lifecycle.InitialisationException;
import org.mule.api.transformer.DataType;
import org.mule.config.i18n.CoreMessages;
import org.mule.el.context.AppContext;
import org.mule.el.context.MuleInstanceContext;
import org.mule.el.context.ServerContext;
import org.mule.transformer.types.DataTypeFactory;

import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.activation.DataHandler;
import javax.activation.MimeType;

import org.mvel2.CompileException;
import org.mvel2.ParserContext;
import org.mvel2.integration.impl.CachedMapVariableResolverFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Expression language that uses MVEL (http://mvel.codehaus.org/).
 */
public class MVELExpressionLanguage implements ExpressionLanguage, Initialisable
{
    private static Logger log = LoggerFactory.getLogger(MVELExpressionLanguage.class);

    protected ParserContext parserContext;
    protected MuleContext muleContext;
    protected MVELExpressionExecutor expressionExecutor;

    // Static context objects
    protected ServerContext serverContext;
    protected MuleInstanceContext muleInstanceContext;
    protected AppContext appContext;

    public MVELExpressionLanguage(MuleContext muleContext)
    {
        this.muleContext = muleContext;
    }

    @Override
    public void initialise() throws InitialisationException
    {
        System.setProperty("mvel2.compiler.allow_override_all_prophandling", "true");

        parserContext = createParserContext();
        expressionExecutor = new MVELExpressionExecutor(parserContext);

        // Static context
        serverContext = new ServerContext();
        muleInstanceContext = new MuleInstanceContext(muleContext);
        appContext = new AppContext(muleContext);
    }

    protected void addExtensions(ExpressionLanguageContext context)
    {
        for (ExpressionLanguageExtension extension : muleContext.getRegistry().lookupObjectsForLifecycle(
            ExpressionLanguageExtension.class))
        {
            extension.configureContext(context);
        }

    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T evaluate(String expression)
    {
        MVELExpressionLanguageContext factory = new MVELExpressionLanguageContext(parserContext, muleContext);
        factory.appendFactory(new RegistryVariableResolverFactory(parserContext, muleContext));
        return (T) evaluateInternal(expression, factory);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T evaluate(String expression, Map<String, Object> vars)
    {
        MVELExpressionLanguageContext factory = new MVELExpressionLanguageContext(parserContext, muleContext);
        factory.appendFactory(new RegistryVariableResolverFactory(parserContext, muleContext));
        if (vars != null)
        {
            factory.appendFactory(new CachedMapVariableResolverFactory(vars));
        }
        return (T) evaluateInternal(expression, factory);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T evaluate(String expression, MuleEvent event)
    {
        return (T) evaluate(expression, event, null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T evaluate(String expression, MuleEvent event, Map<String, Object> vars)
    {
        MVELExpressionLanguageContext factory = new MVELExpressionLanguageContext(parserContext, muleContext);
        factory.appendFactory(new EventVariableResolverFactory(parserContext, muleContext, event));
        factory.appendFactory(new VariableVariableResolverFactory(parserContext, muleContext, event));
        factory.appendFactory(new RegistryVariableResolverFactory(parserContext, muleContext));
        if (vars != null)
        {
            factory.appendFactory(new CachedMapVariableResolverFactory(vars));
        }
        return (T) evaluateInternal(expression, factory);
    }

    @Override
    @SuppressWarnings({"unchecked", "deprecation"})
    public <T> T evaluate(String expression, MuleMessage message)
    {
        MVELExpressionLanguageContext factory = new MVELExpressionLanguageContext(parserContext, muleContext);
        factory.appendFactory(new MessageVariableResolverFactory(parserContext, muleContext, message));
        factory.appendFactory(new VariableVariableResolverFactory(parserContext, muleContext, message));
        factory.appendFactory(new RegistryVariableResolverFactory(parserContext, muleContext));
        return (T) evaluateInternal(expression, factory);
    }

    @SuppressWarnings({"unchecked", "deprecation"})
    @Override
    public <T> T evaluate(String expression, MuleMessage message, Map<String, Object> vars)
    {
        MVELExpressionLanguageContext factory = new MVELExpressionLanguageContext(parserContext, muleContext);
        factory.appendFactory(new MessageVariableResolverFactory(parserContext, muleContext, message));
        factory.appendFactory(new VariableVariableResolverFactory(parserContext, muleContext, message));
        factory.appendFactory(new RegistryVariableResolverFactory(parserContext, muleContext));
        if (vars != null)
        {
            factory.appendFactory(new CachedMapVariableResolverFactory(vars));
        }
        return (T) evaluateInternal(expression, factory);
    }

    @SuppressWarnings("unchecked")
    protected <T> T evaluateInternal(String expression, MVELExpressionLanguageContext variableResolverFactory)
    {
        try
        {
            variableResolverFactory.addFinalVariable("server", serverContext);
            variableResolverFactory.addFinalVariable("mule", muleInstanceContext);
            variableResolverFactory.addFinalVariable("app", appContext);
            addExtensions(variableResolverFactory);
            return (T) expressionExecutor.execute(expression, variableResolverFactory);
        }
        catch (Exception e)
        {
            throw new ExpressionRuntimeException(CoreMessages.expressionEvaluationFailed(expression), e);
        }
    }

    @Override
    public boolean isValid(String expression)
    {
        try
        {
            validate(expression);
            return true;
        }
        catch (InvalidExpressionException e)
        {
            return false;
        }
    }

    @Override
    public void validate(String expression) throws InvalidExpressionException
    {
        try
        {
            expressionExecutor.validate(expression);
        }
        catch (CompileException e)
        {
            throw new InvalidExpressionException(expression, e.getMessage());
        }
    }

    protected ParserContext createParserContext()
    {
        ParserContext parserContext = new ParserContext();
        configureParserContext(parserContext);
        return parserContext;
    }

    protected void configureParserContext(ParserContext parserContext)
    {
        // defaults imports
        parserContext.addImport(Date.class);
        parserContext.addImport(Collection.class);
        parserContext.addImport(List.class);
        parserContext.addImport(Map.class);
        parserContext.addImport(Set.class);
        parserContext.addImport(Boolean.class);
        parserContext.addImport(Byte.class);
        parserContext.addImport(Character.class);
        parserContext.addImport(Float.class);
        parserContext.addImport(Enum.class);
        parserContext.addImport(Integer.class);
        parserContext.addImport(Long.class);
        parserContext.addImport(Math.class);
        parserContext.addImport(Number.class);
        parserContext.addImport(Object.class);
        parserContext.addImport(Short.class);
        parserContext.addImport(String.class);
        parserContext.addImport(System.class);
        parserContext.addImport(Calendar.class);
        parserContext.addImport(DataHandler.class);
        parserContext.addImport(DataType.class);
        parserContext.addImport(DataTypeFactory.class);
        parserContext.addImport(MimeType.class);
    }

}
