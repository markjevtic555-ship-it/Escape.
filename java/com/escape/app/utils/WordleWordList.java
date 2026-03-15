package com.escape.app.utils;

import android.content.Context;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Daje listu 5-slovnih riječi za Wordle igru
 * Podržava više jezika: engleski, srpski cirilica i srpski latinica
 */
public class WordleWordList {
    
    private static final Random random = new Random();
    
    // ZA ENGLESKI WORDLE
    private static final List<String> WORDS_EN = Arrays.asList(
        "ABOUT", "ABOVE", "ABUSE", "ACTOR", "ADAPT", "ADMIT", "ADOPT", "ADULT", "AFTER", "AGAIN",
        "AGENT", "AGREE", "AHEAD", "ALARM", "ALBUM", "ALERT", "ALIKE", "ALIVE", "ALLOW", "ALONE",
        "ALONG", "ALTER", "ANGEL", "ANGER", "ANGLE", "ANGRY", "APART", "APPLE", "APPLY", "ARENA",
        "ARGUE", "ARISE", "ARMOR", "ARRAY", "ARROW", "ASIDE", "ASSET", "AUDIO", "AVOID", "AWARD",
        "AWARE", "BASIC", "BEACH", "BEGIN", "BEING", "BELOW", "BENCH", "BIRTH", "BLACK", "BLAME",
        "BLANK", "BLAST", "BLEND", "BLESS", "BLIND", "BLOCK", "BLOOD", "BLOOM", "BLOWN", "BOARD",
        "BOOST", "BOUND", "BRAIN", "BRAND", "BRAVE", "BREAD", "BREAK", "BREED", "BRICK", "BRIDE",
        "BRIEF", "BRING", "BROAD", "BROKE", "BROWN", "BUILD", "BUILT", "BUYER", "CABIN", "CABLE",
        "CALIF", "CARRY", "CATCH", "CAUSE", "CHAIN", "CHAIR", "CHAOS", "CHARM", "CHART", "CHASE",
        "CHEAP", "CHECK", "CHEST", "CHIEF", "CHILD", "CHINA", "CHOSE", "CHUNK", "CLAIM", "CLASS",
        "CLEAN", "CLEAR", "CLIMB", "CLOCK", "CLOSE", "CLOUD", "COACH", "COAST", "CORAL", "COUCH",
        "COULD", "COUNT", "COURT", "COVER", "CRAFT", "CRASH", "CREAM", "CREEK", "CRIME", "CRISP",
        "CROSS", "CROWD", "CROWN", "CRUEL", "CRUSH", "CURVE", "CYCLE", "DAILY", "DANCE", "DEALT",
        "DEATH", "DEBUT", "DELAY", "DEPTH", "DIARY", "DIRTY", "DOUBT", "DOZEN", "DRAFT", "DRAIN",
        "DRAMA", "DRANK", "DRAWN", "DREAM", "DRESS", "DRIFT", "DRILL", "DRINK", "DRIVE", "DROWN",
        "DUSTY", "EAGER", "EARLY", "EARTH", "EIGHT", "ELITE", "EMPTY", "ENEMY", "ENJOY", "ENTER",
        "ENTRY", "EQUAL", "ERROR", "EVENT", "EVERY", "EXACT", "EXIST", "EXTRA", "FAINT", "FAITH",
        "FALSE", "FANCY", "FATAL", "FAULT", "FAVOR", "FEAST", "FIBER", "FIELD", "FIFTH", "FIFTY",
        "FIGHT", "FINAL", "FIRST", "FIXED", "FLAME", "FLASH", "FLESH", "FLOAT", "FLOOD", "FLOOR",
        "FLOUR", "FLUID", "FOCUS", "FORCE", "FORGE", "FORTH", "FORTY", "FORUM", "FOUND", "FRAME",
        "FRANK", "FRAUD", "FRESH", "FRONT", "FROST", "FRUIT", "FULLY", "FUNNY", "GHOST", "GIANT",
        "GIVEN", "GLASS", "GLOBE", "GLORY", "GLOVE", "GRACE", "GRADE", "GRAIN", "GRAND", "GRANT",
        "GRAPE", "GRAPH", "GRASP", "GRASS", "GRAVE", "GREAT", "GREEN", "GREET", "GRIEF", "GROSS",
        "GROUP", "GROWN", "GUARD", "GUESS", "GUEST", "GUIDE", "GUILT", "HABIT", "HAPPY", "HARSH",
        "HAVEN", "HEART", "HEAVY", "HELLO", "HENCE", "HORSE", "HOTEL", "HOUSE", "HUMAN", "HUMOR",
        "IDEAL", "IMAGE", "IMPLY", "INDEX", "INNER", "INPUT", "IRONY", "ISSUE", "JOINT", "JONES",
        "JUDGE", "JUICE", "KEEPS", "KNIFE", "KNOCK", "KNOWN", "LABEL", "LABOR", "LACKS", "LARGE",
        "LASER", "LATER", "LAUGH", "LAYER", "LEARN", "LEASE", "LEAST", "LEAVE", "LEGAL", "LEMON",
        "LEVEL", "LIGHT", "LIMIT", "LINKS", "LIVED", "LIVER", "LIVES", "LOCAL", "LOGIC", "LOOSE",
        "LOTUS", "LOVER", "LOWER", "LOYAL", "LUCKY", "LUNCH", "LYING", "MAGIC", "MAJOR", "MAKER",
        "MARCH", "MARRY", "MATCH", "MAYBE", "MAYOR", "MEANT", "MEDIA", "MERCY", "MERGE", "MERIT",
        "METAL", "MIDST", "MIGHT", "MINOR", "MINUS", "MIXED", "MODEL", "MONEY", "MONTH", "MORAL",
        "MOTOR", "MOUNT", "MOUSE", "MOUTH", "MOVED", "MOVIE", "MUSIC", "NAKED", "NAVAL", "NERVE",
        "NEVER", "NEWLY", "NIGHT", "NINTH", "NOBLE", "NOISE", "NORTH", "NOTED", "NOVEL", "NURSE",
        "OCCUR", "OCEAN", "OFFER", "OFTEN", "ORDER", "OTHER", "OUGHT", "OUTER", "OWNED", "OWNER",
        "OXIDE", "OZONE", "PAINT", "PANEL", "PANIC", "PAPER", "PARTY", "PASTA", "PATCH", "PAUSE",
        "PEACE", "PEARL", "PENNY", "PHASE", "PHONE", "PHOTO", "PIANO", "PIECE", "PILOT", "PINCH",
        "PITCH", "PLACE", "PLAIN", "PLANE", "PLANT", "PLATE", "PLAZA", "PLEAD", "POINT", "POLAR",
        "POUND", "POWER", "PRESS", "PRICE", "PRIDE", "PRIME", "PRINT", "PRIOR", "PRIZE", "PROBE",
        "PROOF", "PROUD", "PROVE", "PSALM", "PUNCH", "PUPIL", "QUEEN", "QUERY", "QUEST", "QUICK",
        "QUIET", "QUOTE", "RADAR", "RADIO", "RAISE", "RALLY", "RANCH", "RANGE", "RAPID", "RATIO",
        "REACH", "REACT", "READY", "REALM", "REBEL", "REFER", "REIGN", "RELAX", "REPLY", "RESET",
        "RIDER", "RIDGE", "RIFLE", "RIGHT", "RIGID", "RISKY", "RIVAL", "RIVER", "ROBOT", "ROGER",
        "ROMAN", "ROUGH", "ROUND", "ROUTE", "ROYAL", "RUGBY", "RULER", "RURAL", "SADLY", "SAINT",
        "SALAD", "SALES", "SANDY", "SAUCE", "SAVED", "SCALE", "SCENE", "SCOPE", "SCORE", "SCOUT",
        "SENSE", "SERVE", "SETUP", "SEVEN", "SHADE", "SHAKE", "SHALL", "SHAME", "SHAPE", "SHARE",
        "SHARP", "SHEEP", "SHEET", "SHELF", "SHELL", "SHIFT", "SHINE", "SHIRT", "SHOCK", "SHOOT",
        "SHORE", "SHORT", "SHOUT", "SHOWN", "SIGHT", "SIGMA", "SILLY", "SINCE", "SIXTH", "SIXTY",
        "SIZED", "SKILL", "SKULL", "SLAVE", "SLEEP", "SLICE", "SLIDE", "SLOPE", "SMALL", "SMART",
        "SMELL", "SMILE", "SMITH", "SMOKE", "SNAKE", "SOLID", "SOLVE", "SORRY", "SOUND", "SOUTH",
        "SPACE", "SPARE", "SPARK", "SPEAK", "SPEED", "SPELL", "SPEND", "SPENT", "SPICE", "SPINE",
        "SPITE", "SPLIT", "SPOKE", "SPORT", "SPRAY", "SQUAD", "STACK", "STAFF", "STAGE", "STAIN",
        "STAKE", "STAMP", "STAND", "STARK", "START", "STATE", "STAYS", "STEAK", "STEAL", "STEAM",
        "STEEL", "STEEP", "STICK", "STIFF", "STILL", "STOCK", "STONE", "STOOD", "STORE", "STORM",
        "STORY", "STOVE", "STRIP", "STUCK", "STUDY", "STUFF", "STYLE", "SUGAR", "SUITE", "SUPER",
        "SURGE", "SWEAR", "SWEEP", "SWEET", "SWIFT", "SWING", "SWORD", "TABLE", "TAKEN", "TASTE",
        "TAXES", "TEACH", "TEETH", "TEMPO", "TENDS", "TENSE", "TENTH", "TERMS", "TEXAS", "THANK",
        "THEFT", "THEIR", "THEME", "THERE", "THESE", "THICK", "THIEF", "THING", "THINK", "THIRD",
        "THOSE", "THREE", "THREW", "THROW", "THUMB", "TIGER", "TIGHT", "TIRED", "TITLE", "TODAY",
        "TOKEN", "TONES", "TOPIC", "TOTAL", "TOUCH", "TOUGH", "TOWER", "TOXIC", "TRACE", "TRACK",
        "TRADE", "TRAIL", "TRAIN", "TRASH", "TREAT", "TREND", "TRIAL", "TRIBE", "TRICK", "TRIED",
        "TRIPS", "TROOP", "TRUCK", "TRULY", "TRUMP", "TRUNK", "TRUST", "TRUTH", "TWICE", "TWIST",
        "TYLER", "ULTRA", "UNCLE", "UNDER", "UNION", "UNITE", "UNITY", "UNTIL", "UPPER", "UPSET",
        "URBAN", "USAGE", "USUAL", "VALID", "VALUE", "VALVE", "VENUE", "VERSE", "VIDEO", "VIGOR",
        "VIRAL", "VIRUS", "VISIT", "VITAL", "VIVID", "VOCAL", "VOICE", "VOTER", "WAGON", "WASTE",
        "WATCH", "WATER", "WEIGH", "WEIRD", "WHALE", "WHEAT", "WHEEL", "WHERE", "WHICH", "WHILE",
        "WHITE", "WHOLE", "WHOSE", "WIDTH", "WOMAN", "WORLD", "WORRY", "WORSE", "WORST", "WORTH",
        "WOULD", "WOUND", "WRIST", "WRITE", "WRONG", "WROTE", "YIELD", "YOUNG", "YOUTH", "ZEBRA"
    );
    
    // ĆIRILIČNI SRPSKI WORDLE
    private static final List<String> WORDS_SR_CYRILLIC = Arrays.asList(
        "МАЈКА", "ШКОЛА", "ГЛАВА", "СУНЦЕ", "МЕСЕЦ", "ДАНАС", "СУТРА", "ВРЕМЕ", "МИНУТ", "ЈУТРО",
        "ПОДНЕ", "ЈЕСЕН", "ВЕТАР", "МАЧКА", "ПЕТЛО", "КОКОШ", "ПТИЦА", "МЛЕКО", "БАНЈА", "ВРАТА",
        "ЛАМПА", "АВИОН", "МОТОР", "УЛИЦА", "ЈЕЗЕР", "ТУНЕЛ", "ЗЕМЉА", "КУЋА", "ПОЉЕ", "ЖИВОТ",
        "РУЖА", "СВИЊА", "МЕДВЕД", "ЛИСИЦА", "КОМАРА", "САЛАТА", "РАКИЈА", "ШЕЋЕРА", "КУХИЊА", "СТОЛИЦА",
        "ТЕЛЕВИЗОР", "РАЧУНАРА", "ТЕЛЕФОНА", "МОБИЛНИ", "ТАБЛЕТА", "ЛАПТОПА", "АВТОБУСА", "ТРОТИНЕТА", "ТАКСИЈА", "ТРАМВАЈА",
        "ДВОРИШТА", "БАШТА", "ЦВЕТА", "ДРВЕТА", "ЛИСТА", "ГРАНА", "КОРЕНА", "СТАБЛА", "ПТИЦЕ", "ГНЕЗДА",
        "ПИЛЕТА", "КОКОШКА", "ПЕТЛА", "КУЧЕТА", "МАЧКЕ", "МАЧИЋА", "ЖИВОТА", "СВЕТА", "ДАНА", "НОЋИ",
        "САТА", "СЕКУН", "ГОДИН", "НОЋ", "ЗИМА", "ПРОЛЕ", "ЛЕТО", "КИША", "СНЕГ", "ОГЊА",
        "ВАЗДУХ", "БОЈА", "ЦРВЕНА", "ПЛАВА", "ЖУТА", "ЗЕЛЕНА", "БЕЛА", "ЦРНА", "СИВА", "КОЊА",
        "КРАВА", "ОВЦА", "КОЗА", "РИБА", "ВУКА", "ЗЕЦА", "МИША", "ПАУКА", "ХЛЕБА", "МЕСА",
        "СИРА", "ЈАЈА", "ВОЋЕ", "СУПА", "ПИЦА", "КАФА", "ЧАЈА", "ВОДА", "СОКА", "ПИВА",
        "ВИНА", "СОЛИ", "БИБЕРА", "СОБА", "СТОЛА", "КРЕВЕТА", "ПРОЗОРА", "КЛУЧА", "АУТА", "ВОЗА",
        "БРОДА", "ГРАДА", "СЕЛА", "ТРГА", "ПАРКА", "ШУМА", "РЕКА", "МОРА", "ПУТА", "МОСТА",
        "ОСТРВА", "ПЕШЧАНА", "КЛИСУРА", "ДВОРИШТА", "БАШТА", "ЦВЕТА", "ДРВЕТА", "ЛИСТА", "ГРАНА", "КОРЕНА"
    );
    
    // LTINIČNI SRPSKI WORDLE
    private static final List<String> WORDS_SR_LATIN = Arrays.asList(
        "MAJKA", "ŠKOLA", "GLAVA", "SUNCE", "MESEC", "DANAS", "SUTRA", "VREME", "MINUT", "JUTRO",
        "PODNE", "JESEN", "VETAR", "MAČKA", "PETLO", "KOKOŠ", "PTICA", "MLEKO", "BANJA", "VRATA",
        "LAMPA", "AVION", "MOTOR", "ULICA", "JEZER", "TUNEL", "ZEMLJA", "KUĆA", "POLJE", "ŽIVOT",
        "RUŽA", "SVINJA", "MEDVED", "LISICA", "KOMARA", "SALATA", "RAKIJA", "ŠEĆERA", "KUHINJA", "STOLICA",
        "TELEVIZOR", "RAČUNARA", "TELEFONA", "MOBILNI", "TABLETA", "LAPTOPA", "AUTOBUSA", "TROTINETA", "TAKSIJA", "TRAMVAJA",
        "DVORIŠTA", "BAŠTA", "CVETA", "DRVETA", "LISTA", "GRANA", "KORENA", "STABLA", "PTICE", "GNEZDA",
        "PILETA", "KOKOŠKA", "PETLA", "KUČETA", "MAČKE", "MAČIĆA", "ŽIVOTA", "SVETA", "DANA", "NOĆI",
        "SATA", "SEKUN", "GODIN", "NOĆ", "ZIMA", "PROLE", "LETO", "KIŠA", "SNEG", "OGNJA",
        "VAZDUH", "BOJA", "CRVENA", "PLAVA", "ŽUTA", "ZELENA", "BELO", "CRNO", "SIVO", "KONJA",
        "KRAVA", "OVCA", "KOZA", "RIBA", "VUKA", "ZECA", "MIŠA", "PAUKA", "HLEBA", "MESA",
        "SIRA", "JAJA", "VOĆE", "SUPA", "PICA", "KAFA", "ČAJA", "VODA", "SOKA", "PIVA",
        "VINA", "SOLI", "BIBERA", "SOBA", "STOLA", "KREVETA", "PROZORA", "KLUČA", "AUTA", "VOZA",
        "BRODA", "GRADA", "SELA", "TRGA", "PARKA", "ŠUMA", "REKA", "MORA", "PUTA", "MOSTA",
        "OSTRVA", "PEŠČANA", "KLISURA", "DVORIŠTA", "BAŠTA", "CVETA", "DRVETA", "LISTA", "GRANA", "KORENA"
    );

    public static String getRandomWord(Context context) {
        String language = LocaleHelper.getLanguage(context);
        List<String> words;
        
        if (language.equals("sr-Latn")) {
            words = WORDS_SR_LATIN;
        } else if (language.equals("sr")) {
            words = WORDS_SR_CYRILLIC;
        } else {
            words = WORDS_EN;
        }
        
        return words.get(random.nextInt(words.size()));
    }
    

    public static boolean isValidWord(Context context, String word) {
        String language = LocaleHelper.getLanguage(context);
        List<String> words;
        
        if (language.equals("sr-Latn")) {
            words = WORDS_SR_LATIN;
        } else if (language.equals("sr")) {
            words = WORDS_SR_CYRILLIC;
        } else {
            words = WORDS_EN;
        }
        
        return words.contains(word.toUpperCase());
    }
    

    public static int getWordCount(Context context) {
        String language = LocaleHelper.getLanguage(context);
        List<String> words;
        
        if (language.equals("sr-Latn")) {
            words = WORDS_SR_LATIN;
        } else if (language.equals("sr")) {
            words = WORDS_SR_CYRILLIC;
        } else {
            words = WORDS_EN;
        }
        
        return words.size();
    }
    

    public static String[] getKeyboardRows(Context context) {
        String language = LocaleHelper.getLanguage(context);
        
        if (language.equals("sr-Latn")) {
            // Serbian Latin keyboard (QWERTZ layout with Serbian letters)
            return new String[]{
                "QWERTZUIOPŠĐ",
                "ASDFGHJKLČĆŽ",
                "YXCVBNM"
            };
        } else if (language.equals("sr")) {
            // Serbian Cyrillic keyboard
            return new String[]{
                "ЉЊЕРТЗУИОПШЂ",
                "АСДФГХЈКЛЧЋ",
                "ЖЂЗЦВБНМ"
            };
        } else {
            // English keyboard
            return new String[]{
                "QWERTYUIOP",
                "ASDFGHJKL",
                "ZXCVBNM"
            };
        }
    }
}
