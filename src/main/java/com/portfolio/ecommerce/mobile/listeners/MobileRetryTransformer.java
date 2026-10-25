package com.portfolio.ecommerce.mobile.listeners;

import org.testng.IAnnotationTransformer;
import org.testng.annotations.ITestAnnotation;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Attaches {@link MobileRetryAnalyzer} to every @Test in the suite this is registered on.
 *
 * Doing it here rather than with retryAnalyzer = ... on each @Test keeps the retry policy in
 * one place and means a new test picks it up for free. Registered only in the mobile suite
 * XMLs, so the web suites are unaffected.
 *
 * A retryAnalyzer already set explicitly on a test is left alone — an author who opted out
 * (or opted into something stricter) meant it.
 */
public class MobileRetryTransformer implements IAnnotationTransformer {

    // Raw Class/Constructor are what TestNG's interface declares — generics here would
    // clash on erasure and silently fail to override.
    @Override
    @SuppressWarnings("rawtypes")
    public void transform(ITestAnnotation annotation,
                          Class testClass,
                          Constructor testConstructor,
                          Method testMethod) {
        if (annotation.getRetryAnalyzerClass() == null
                || annotation.getRetryAnalyzerClass() == org.testng.internal.annotations.DisabledRetryAnalyzer.class) {
            annotation.setRetryAnalyzer(MobileRetryAnalyzer.class);
        }
    }
}
