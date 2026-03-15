package com.escape.app.utils;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.LocaleList;
import android.util.Log;
import android.util.TypedValue;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

// utility klasa za upravljanje app locale/jezikom – handluje srpski latinica/cirilica i engleski
public class LocaleHelper {
    private static final String DEFAULT_LANGUAGE = "en";
    private static final String SERBIAN_CYRILLIC = "sr";
    private static final String SERBIAN_LATIN = "sr-Latn";
    
    // static referenca na trenutnu LatinResources instancu (za fallback pristup)
    private static LatinResources sLatinResources = null;

    // setuje app locale na osnovu sačuvane preference
    public static Context setLocale(Context context) {
        PreferencesManager preferencesManager = new PreferencesManager(context);
        String language = preferencesManager.getAppLanguage();
        return setLocale(context, language);
    }

    // setuje app locale na određeni jezik
    public static Context setLocale(Context context, String languageCode) {
        Locale locale;
        boolean useLatinResources = false;
        
        if (languageCode.equals(SERBIAN_LATIN)) {
            // srpski (latinica) – koristim srpski locale ali učitavam iz assets
            locale = new Locale("sr");
            useLatinResources = true;
        } else if (languageCode.equals(SERBIAN_CYRILLIC)) {
            // srpski (cirilica) – default srpski
            locale = new Locale("sr");
        } else {
            // default na engleski
            locale = new Locale(DEFAULT_LANGUAGE);
        }

        Locale.setDefault(locale);

        // uzimam application context za asset loading
        Context appContext = context.getApplicationContext();
        if (appContext == null) {
            appContext = context;
        }

        // uzimam cirilične Resources PRE bilo kakvih locale promjena (za pravljenje reverse mape)
        Resources cyrillicResources = null;
        if (useLatinResources) {
            // uzimam Resources iz originalnog contexta prije locale promjena
            // unwrapujem context ako treba da uzmem base Resources
            Context baseContext = context;
            while (baseContext instanceof ContextWrapper && baseContext != appContext) {
                baseContext = ((ContextWrapper) baseContext).getBaseContext();
            }
            // pravim privremeni context sa srpskim localeom da uzmem cirilične stringove
            Configuration cyrillicConfig = baseContext.getResources().getConfiguration();
            cyrillicConfig.setLocale(new Locale("sr"));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                cyrillicConfig.setLocales(new LocaleList(new Locale("sr")));
            }
            Context cyrillicContext = baseContext.createConfigurationContext(cyrillicConfig);
            cyrillicResources = cyrillicContext.getResources();
        }

        Resources resources = context.getResources();
        Configuration configuration = resources.getConfiguration();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            configuration.setLocale(locale);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                configuration.setLocales(new LocaleList(locale));
            }
            Context configContext = context.createConfigurationContext(configuration);
            
            // wrapujem context da učitam latiničke resurse kad treba
            if (useLatinResources) {
                return new LatinResourceContextWrapper(configContext, appContext, cyrillicResources);
            }
            return configContext;
        } else {
            configuration.locale = locale;
            resources.updateConfiguration(configuration, resources.getDisplayMetrics());
            
            // wrapujem context da učitam latiničke resurse kad treba
            if (useLatinResources) {
                return new LatinResourceContextWrapper(context, appContext, cyrillicResources);
            }
            return context;
        }
    }
    
    // context wrapper koji učitava resurse iz assets/strings_sr_latin.xml
    private static class LatinResourceContextWrapper extends ContextWrapper {
        private Context appContext; // originalni application context za asset pristup
        private Resources baseCyrillicResources; // originalni cirilični Resources (prije wrapovanja)
        private LatinResources cachedResources; // cacheujem Resources instancu
        
        public LatinResourceContextWrapper(Context base, Context appContext, Resources cyrillicResources) {
            super(base);
            this.appContext = appContext;
            this.baseCyrillicResources = cyrillicResources;
        }
        
        @Override
        public Resources getResources() {
            // cacheujem Resources instancu da osiguram konzistentnost
            if (cachedResources == null) {
                Resources baseRes = super.getResources();
                cachedResources = new LatinResources(baseRes, appContext, baseCyrillicResources);
                // čuvam static referencu za fallback pristup
                LocaleHelper.sLatinResources = cachedResources;
            }
            return cachedResources;
        }
        
        @Override
        public Context getApplicationContext() {
            // vraćam wrapped application context ako postoji
            Context appCtx = super.getApplicationContext();
            if (appCtx != null && appCtx != appContext) {
                // ako je app context drugačiji, možda treba da ga wrapujem također
                // ali za sada samo vraćam originalni app context
                return appContext;
            }
            return appContext;
        }
        
        @Override
        public Object getSystemService(String name) {
            // KRITIČNO: osiguravam da LayoutInflater koristi moj wrapped context
            // LayoutInflater koristi getResources() iz contexta proslijeđenog LayoutInflater.from()
            if (Context.LAYOUT_INFLATER_SERVICE.equals(name)) {
                android.view.LayoutInflater inflater = (android.view.LayoutInflater) super.getSystemService(name);
                if (inflater != null) {
                    // kloniram inflater i setujem moj context
                    // ovo osigurava da inflater koristi moj Resources wrapper
                    return inflater.cloneInContext(this);
                }
                return inflater;
            }
            return super.getSystemService(name);
        }
    }
    
    // resources wrapper koji učitava iz assets/strings_sr_latin.xml
    // pošto android ne podržava custom resource qualifiere, učitavam latiničke stringove
    // iz assets i overrideujem getString da ih vratim
    static class LatinResources extends Resources {
        private Resources baseResources;
        private Map<String, String> latinStrings;
        private Map<String, String> cyrillicToLatinMap; // reverse mapa za workaround
        private static final String TAG = "LatinResources";
        
        public LatinResources(Resources base, Context context, Resources cyrillicResources) {
            super(base.getAssets(), base.getDisplayMetrics(), base.getConfiguration());
            this.baseResources = base;
            this.latinStrings = loadLatinStrings(context);
            this.cyrillicToLatinMap = buildCyrillicToLatinMap(context, cyrillicResources);
        }
        
        // učitava latiničke stringove iz assets/strings_sr_latin.xml
        private Map<String, String> loadLatinStrings(Context context) {
            Map<String, String> strings = new HashMap<>();
            try {
                AssetManager assetManager = context.getAssets();
                InputStream inputStream = assetManager.open("strings_sr_latin.xml");
                
                XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
                factory.setNamespaceAware(false);
                XmlPullParser parser = factory.newPullParser();
                parser.setInput(inputStream, "UTF-8");
                
                int eventType = parser.getEventType();
                String currentName = null;
                StringBuilder textBuilder = new StringBuilder();
                boolean insideString = false;
                
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG) {
                        if ("string".equals(parser.getName())) {
                            currentName = parser.getAttributeValue(null, "name");
                            textBuilder.setLength(0);
                            insideString = true;
                        }
                    } else if (eventType == XmlPullParser.TEXT) {
                        if (insideString && currentName != null) {
                            String text = parser.getText();
                            if (text != null) {
                                textBuilder.append(text);
                            }
                        }
                    } else if (eventType == XmlPullParser.END_TAG) {
                        if ("string".equals(parser.getName())) {
                            if (currentName != null) {
                                String value = textBuilder.toString().trim();
                                if (!value.isEmpty()) {
                                    // popravljam newline probleme: zamjenjujem literal "/n" sa pravim newline "\n"
                                    // također osiguravam da se "\n" sekvence pravilno interpretiraju
                                    value = value.replace("/n", "\n");
                                    // handlujem escaped newlineove koji su možda double-escaped
                                    value = value.replace("\\n", "\n");
                                    strings.put(currentName, value);
                                } else {
                                    Log.w(TAG, "Empty value for string: " + currentName);
                                }
                            }
                            currentName = null;
                            textBuilder.setLength(0);
                            insideString = false;
                        }
                    }
                    eventType = parser.next();
                }
                
                inputStream.close();
                if (strings.size() == 0) {
                    Log.e(TAG, "WARNING: No Latin strings loaded! Check assets file.");
                }
            } catch (IOException e) {
                Log.e(TAG, "IO Error loading Latin strings from assets", e);
                e.printStackTrace();
            } catch (XmlPullParserException e) {
                Log.e(TAG, "XML Parse Error loading Latin strings from assets", e);
                e.printStackTrace();
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error loading Latin strings from assets", e);
                e.printStackTrace();
            }
            return strings;
        }
        
        // uzima latinički string po resource name (koristi se interno)
        private String getLatinString(String resourceName) {
            if (resourceName != null && latinStrings.containsKey(resourceName)) {
                String latinValue = latinStrings.get(resourceName);
                if (latinValue != null && !latinValue.isEmpty()) {
                    return latinValue;
                }
            }
            return null;
        }
        
        // uzima latinički string po resource name (izložen za LocaleHelper)
        String getLatinStringByName(String resourceName) {
            return getLatinString(resourceName);
        }
        
        // uzima latinički string za dati cirilični tekst (workaround za bypassed Resources)
        String getLatinStringForCyrillic(String cyrillicText) {
            if (cyrillicText == null || cyrillicToLatinMap == null) {
                return null;
            }
            return cyrillicToLatinMap.get(cyrillicText);
        }
        
        // pravi reverse mapu od ciriličnih stringova do latiničkih stringova
        private Map<String, String> buildCyrillicToLatinMap(Context context, Resources cyrillicResources) {
            Map<String, String> map = new HashMap<>();
            try {
                // koristim proslijeđene cirilične Resources (ne moj wrapped Resources)
                // uzimam sve string resource IDove
                // pravim mapu iteriranjem kroz poznate resource nameove
                for (String resourceName : latinStrings.keySet()) {
                    try {
                        int id = cyrillicResources.getIdentifier(resourceName, "string", context.getPackageName());
                        if (id != 0) {
                            String cyrillic = cyrillicResources.getString(id);
                            String latin = latinStrings.get(resourceName);
                            if (cyrillic != null && latin != null && !cyrillic.equals(latin)) {
                                map.put(cyrillic, latin);
                                Log.v(TAG, "Mapped: " + resourceName + " -> " + cyrillic.substring(0, Math.min(30, cyrillic.length())) + " -> " + latin.substring(0, Math.min(30, latin.length())));
                            }
                        }
                    } catch (Exception e) {
                        // preskačem ovaj resource
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error building Cyrillic->Latin map", e);
            }
            return map;
        }
        
        @Override
        public CharSequence getText(int id) throws NotFoundException {
            // uvijek logujem da provjerim da li se ova metoda poziva
            Log.i(TAG, "🔍 getText(int) CALLED for id=" + id + " (this=" + this.getClass().getSimpleName() + ")");
            return getTextInternal(id);
        }
        
        @Override
        public CharSequence getText(int id, CharSequence def) {
            // ova varijanta se koristi od strane TypedArray i drugih android internals
            Log.i(TAG, "🔍 getText(int, CharSequence) CALLED for id=" + id);
            try {
                CharSequence result = getTextInternal(id);
                if (result != null) {
                    return result;
                }
            } catch (Resources.NotFoundException e) {
                // resource nije nađen, vraćam default
            } catch (Exception e) {
                Log.e(TAG, "Error in getText(int, CharSequence) for id=" + id, e);
            }
            return def;
        }
        
        private CharSequence getTextInternal(int id) throws NotFoundException {
            try {
                String resourceName = baseResources.getResourceEntryName(id);
                String latinValue = getLatinString(resourceName);
                if (latinValue != null) {
                    return latinValue;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in getTextInternal() for id=" + id, e);
            }
            return baseResources.getText(id);
        }
        
        @Override
        public void getValue(int id, TypedValue outValue, boolean resolveRefs) throws NotFoundException {
            // ova metoda se koristi od strane android resource sistema
            // moram da je interceptujem da vratim latiničke stringove
            try {
                String resourceName = baseResources.getResourceEntryName(id);
                String latinValue = getLatinString(resourceName);
                if (latinValue != null) {
                    // pravim TypedValue sa latiničkim stringom
                    outValue.type = TypedValue.TYPE_STRING;
                    outValue.string = latinValue;
                    outValue.data = 0;
                    return;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in getValue() for id=" + id, e);
            }
            baseResources.getValue(id, outValue, resolveRefs);
        }
        
        @Override
        public String getString(int id) throws NotFoundException {
            try {
                String resourceName = baseResources.getResourceEntryName(id);
                String latinValue = getLatinString(resourceName);
                if (latinValue != null) {
                    return latinValue;
                }
            } catch (Resources.NotFoundException e) {
                // resource ne postoji, puštam base da ga handluje
            } catch (Exception e) {
                Log.w(TAG, "Error getting Latin string for id: " + id, e);
            }
            return baseResources.getString(id);
        }
        
        @Override
        public String getString(int id, Object... formatArgs) throws NotFoundException {
            try {
                String resourceName = baseResources.getResourceEntryName(id);
                String format = getLatinString(resourceName);
                if (format != null) {
                    return String.format(format, formatArgs);
                }
            } catch (Resources.NotFoundException e) {
                // resource ne postoji, puštam base da ga handluje
            } catch (Exception e) {
                Log.w(TAG, "Error getting Latin string for id: " + id, e);
            }
            return baseResources.getString(id, formatArgs);
        }
    }

    // uzima trenutni language code
    public static String getLanguage(Context context) {
        PreferencesManager preferencesManager = new PreferencesManager(context);
        return preferencesManager.getAppLanguage();
    }

    // provjerava da li je trenutni jezik srpski (bilo koja varijanta)
    public static boolean isSerbian(Context context) {
        String lang = getLanguage(context);
        return lang.equals(SERBIAN_CYRILLIC) || lang.equals(SERBIAN_LATIN);
    }
    
    // uzima latinički string za dati cirilični tekst
    // ovo je workaround kad android zaobiđe moj Resources wrapper
    public static String getLatinStringForCyrillic(Context context, String cyrillicText) {
        if (!getLanguage(context).equals(SERBIAN_LATIN)) {
            return null; // ne koristim latinicu
        }
        
        // pokušavam da uzmem latinički string iz mog cached Resources
        // prvo pokušavam contextov Resources
        Resources resources = context.getResources();
        if (resources instanceof LatinResources) {
            return ((LatinResources) resources).getLatinStringForCyrillic(cyrillicText);
        }
        
        // ako contextov Resources nije LatinResources, pokušavam Application context
        Context appContext = context.getApplicationContext();
        if (appContext != null && appContext != context) {
            Resources appResources = appContext.getResources();
            if (appResources instanceof LatinResources) {
                return ((LatinResources) appResources).getLatinStringForCyrillic(cyrillicText);
            }
        }
        
        // finalni fallback: koristim static referencu ako je dostupna
        if (sLatinResources != null) {
            return sLatinResources.getLatinStringForCyrillic(cyrillicText);
        }
        
        return null;
    }
    
    // izlazim metodu da uzmem latinički string po resource name (za BaseActivity)
    public static String getLatinStringByName(Context context, String resourceName) {
        Resources resources = context.getResources();
        if (resources instanceof LatinResources) {
            return ((LatinResources) resources).getLatinStringByName(resourceName);
        }
        return null;
    }
}
