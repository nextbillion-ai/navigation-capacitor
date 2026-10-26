package com.cargix.navigationdemo;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.concurrent.*;
import static org.junit.Assert.*;

/** Exercise the real example form's Apply/Cancel/defaults and per-stop alignment without a key. */
@RunWith(AndroidJUnit4.class)
public class RouteSettingsTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private MainActivity activity;

    private String js(String code) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        String[] value = {null};
        instrumentation.runOnMainSync(() -> activity.getBridge().getWebView().evaluateJavascript(code, result -> { value[0] = result; done.countDown(); }));
        assertTrue("WebView evaluation timed out", done.await(10, TimeUnit.SECONDS));
        return value[0];
    }

    private void waitTrue(String code) throws Exception {
        long end = SystemClock.elapsedRealtime() + 10000;
        while (SystemClock.elapsedRealtime() < end) {
            if ("true".equals(js(code))) return;
            SystemClock.sleep(100);
        }
        fail("Condition not reached: " + code);
    }

    private void open() throws Exception {
        js("document.getElementById('settings-open').click();true");
        waitTrue("!document.getElementById('settings-modal').hidden");
        js("document.querySelector('[data-tab=route]').click();true");
    }

    private void apply() throws Exception {
        js("document.getElementById('settings-save').click();true");
        waitTrue("document.getElementById('settings-modal').hidden");
    }

    @Test public void customPreferencesCancelDefaultsAndStopChanges() throws Exception {
        Intent intent = new Intent(instrumentation.getTargetContext(), MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity = (MainActivity) instrumentation.startActivitySync(intent);
        waitTrue("document.getElementById('preset')?.options.length > 0");
        open();
        assertEquals("true", js("document.getElementById('route-departure-mode').value === '' && document.getElementById('route-language').value === '' && document.getElementById('route-unit').value === ''"));
        js("(() => { const set=(id,value)=>{const e=document.getElementById(id);e.value=value;e.dispatchEvent(new Event('change',{bubbles:true}));};set('route-departure-mode','custom');set('route-departure-time','2030-01-02T09:15:00');set('route-cross-border','false');set('route-language','fr');set('route-unit','imperial');set('route-approaches-mode','custom');document.querySelector('#route-approaches select').value='curb';const sharp=document.querySelector('#avoid-options input[value=sharp_turn]');sharp.checked=true;sharp.dispatchEvent(new Event('change',{bubbles:true}));set('route-turn-angle','1');return true;})()");
        apply();
        open();
        assertEquals("true", js("document.getElementById('route-departure-time').value.startsWith('2030-01-02T09:15') && document.getElementById('route-cross-border').value === 'false' && document.getElementById('route-language').value === 'fr' && document.getElementById('route-unit').value === 'imperial' && document.getElementById('route-turn-angle').value === '1' && document.querySelector('#route-approaches select').value === 'curb'"));
        if ("true".equals(InstrumentationRegistry.getArguments().getString("captureSettings"))) {
            instrumentation.waitForIdleSync();
            SystemClock.sleep(300);
            android.graphics.Bitmap screenshot = instrumentation.getUiAutomation().takeScreenshot();
            assertNotNull(screenshot);
            try (java.io.FileOutputStream output = new java.io.FileOutputStream(new java.io.File(activity.getFilesDir(), "route-settings.png"))) {
                screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output);
            }
            screenshot.recycle();
        }
        js("document.getElementById('route-language').value='de';document.getElementById('settings-cancel').click();true");
        open();
        assertEquals("\"fr\"", js("document.getElementById('route-language').value"));
        js("document.getElementById('settings-cancel').click();document.getElementById('add').click();true");
        open();
        assertEquals("true", js("(() => {const values=Array.from(document.querySelectorAll('#route-approaches select'),e=>e.value);return values.length===2 && values[0]==='unrestricted' && values[1]==='curb';})()"));
        js("document.getElementById('settings-cancel').click();document.querySelector('#stops .remove').click();true");
        open();
        assertEquals("true", js("document.querySelectorAll('#route-approaches select').length===1 && document.querySelector('#route-approaches select').value==='curb'"));
        js("document.getElementById('route-defaults').click();document.getElementById('route-departure-mode').value='custom';document.getElementById('route-departure-mode').dispatchEvent(new Event('change'));document.getElementById('settings-save').click();true");
        assertEquals("true", js("!document.getElementById('settings-modal').hidden && document.getElementById('settings-error').textContent.length > 0"));
        js("document.getElementById('route-defaults').click();true");
        apply();
        open();
        assertEquals("true", js("['route-departure-mode','route-departure-time','route-cross-border','route-language','route-unit','route-turn-angle','route-approaches-mode'].every(id=>document.getElementById(id).value==='')"));
    }
}
