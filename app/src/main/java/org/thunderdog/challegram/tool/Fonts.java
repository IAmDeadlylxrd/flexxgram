/**
 * File created on 24/04/15 at 21:06
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.tool;

import android.content.res.AssetManager;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Build;
import android.text.TextPaint;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.unsorted.Settings;

import me.vkryl.core.lambda.Future;

public class Fonts {
  private static boolean character_support_inited;

  private static void initCharacterSupport () {
    if (!character_support_inited) {
      synchronized (Fonts.class) {
        if (!character_support_inited) {
          Rect rect = new Rect();
          Paints.getTextPaint15().getTextBounds(Strings.LTR_CHAR, 0, Strings.LTR_CHAR.length(), rect);
          LTR_CHAR_SUPPORTED = rect.left == 0 && rect.right == 0 && rect.top == 0 && rect.bottom == 0;
          Paints.getTextPaint15().getTextBounds(Strings.SCOPE_END, 0, Strings.SCOPE_END.length(), rect);
          SCOPE_END_SUPPORTED = rect.left == 0 && rect.right == 0 && rect.top == 0 && rect.bottom == 0;
          character_support_inited = true;
        }
      }
    }
  }

  private static boolean LTR_CHAR_SUPPORTED = true;
  private static boolean RTL_CHAR_SUPPORTED = true;
  private static boolean SCOPE_END_SUPPORTED = true;

  private static Boolean needSystemFonts;
  private static Typeface robotoRegular, robotoBold, robotoMedium, robotoItalic, robotoMono;

  private static Typeface loadBuiltinFont (String path) {
    AssetManager manager = UI.getContext().getResources().getAssets();
    return Typeface.createFromAsset(manager, path);
  }

  @Nullable
  public static Boolean areUsingSystemFonts () {
    return needSystemFonts;
  }

  private static synchronized Typeface loadFont (String path, @NonNull Future<Typeface> fallback) {
    boolean firstFont = false;
    if (needSystemFonts == null) {
      needSystemFonts = Settings.instance().useSystemFonts();
      firstFont = true;
    }
    if (needSystemFonts) {
      return fallback.get();
    }
    try {
      return loadBuiltinFont(path);
    } catch (Throwable t) {
      if (firstFont)
        needSystemFonts = true;
      Log.e("Unable to load built-in font", t);
      return fallback.get();
    }
  }

  private static Typeface loadSystemFont (String fontFamily, int style, @Nullable Typeface fallback) {
    try {
      Typeface typeface = Typeface.create(fontFamily, style);
      if (typeface.getStyle() == style || style == Typeface.NORMAL)
        return typeface;
    } catch (Throwable t) {
      Log.i("%s %d not found", t, fontFamily, style);
    }
    return fallback;
  }

  private static final boolean LOAD_SANS = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;

  public static Typeface getRobotoRegular () {
    return robotoRegular != null ? robotoRegular : (robotoRegular = loadFont("fonts/Roboto-Regular.ttf", () ->
      LOAD_SANS ? loadSystemFont("sans-serif", Typeface.NORMAL, Typeface.DEFAULT) : Typeface.DEFAULT
    ));
  }

  public static Typeface getRobotoBold () {
    return robotoBold != null ? robotoBold : (robotoBold = loadFont("fonts/Roboto-Bold.ttf", () -> {
      if (LOAD_SANS) {
        // return loadSystemFont("sans-serif-black", Typeface.BOLD, Typeface.DEFAULT_BOLD);
        return loadSystemFont("sans-serif", Typeface.BOLD, Typeface.DEFAULT_BOLD);
      } else {
        return Typeface.DEFAULT_BOLD;
      }
    }));
  }

  public static Typeface getRobotoMedium () {
    return robotoMedium != null ? robotoMedium : (robotoMedium = loadFont("fonts/Roboto-Medium.ttf", () -> {
      if (LOAD_SANS) {
        Typeface typeface = loadSystemFont("sans-serif-light", Typeface.BOLD, null);
        return typeface != null ? typeface : loadSystemFont("sans-serif-medium", Typeface.NORMAL, Typeface.DEFAULT_BOLD);
      } else {
        return Typeface.DEFAULT_BOLD;
      }
    }));
  }

  public static Typeface getRobotoItalic () {
    return robotoItalic != null ? robotoItalic : (robotoItalic = loadFont("fonts/Roboto-Italic.ttf", () ->
      LOAD_SANS ? loadSystemFont("sans-serif", Typeface.ITALIC, Typeface.defaultFromStyle(Typeface.ITALIC)) : Typeface.defaultFromStyle(Typeface.ITALIC)
    ));
  }

  public static Typeface getRobotoMono () {
    return robotoMono != null ? robotoMono : (robotoMono = loadFont("fonts/RobotoMono-Regular.ttf", () ->
      Typeface.MONOSPACE
    ));
  }

  public static final float TEXT_SKEW_ITALIC = -.2f;

  public static boolean isLtrCharSupported () {
    if (!character_support_inited)
      initCharacterSupport();
    return LTR_CHAR_SUPPORTED;
  }

  public static boolean isRtlCharSupported () {
    if (!character_support_inited)
      initCharacterSupport();
    return RTL_CHAR_SUPPORTED;
  }

  public static boolean isScopeEndSupported () {
    if (!character_support_inited)
      initCharacterSupport();
    return SCOPE_END_SUPPORTED;
  }

  public static class TextPaintStorage {
    private TextPaint regularPaint, boldPaint, fakeBoldPaint, italicPaint, boldItalicPaint;

    private final @NonNull Typeface regularTypeface;
    private final @Nullable Typeface boldTypeface, italicTypeface, boldItalicTypeface, monospaceTypeface;

    private TextPaintStorage monospaceStorage;
    private TextPaintStorage underlineStorage;
    private TextPaintStorage strikeThroughStorage;

    private final int paintFlags;

    public TextPaintStorage (@NonNull Typeface regularTypeface, int paintFlags) {
      this(regularTypeface, null, null, null, null, paintFlags);
    }

    public TextPaintStorage (@NonNull Typeface regularTypeface, @Nullable Typeface boldTypeface, @Nullable Typeface italicTypeface, @Nullable Typeface boldItalicTypeface, @Nullable Typeface monospaceTypeface, int paintFlags) {
      this.regularTypeface = regularTypeface;
      this.boldTypeface = boldTypeface;
      this.italicTypeface = italicTypeface;
      this.boldItalicTypeface = boldItalicTypeface;
      this.monospaceTypeface = monospaceTypeface;
      this.paintFlags = paintFlags;
    }

    public TextPaint getRegularPaint () {
      if (regularPaint == null) {
        regularPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | paintFlags);
        regularPaint.setTypeface(regularTypeface);
      }
      return regularPaint;
    }

    public TextPaint getItalicPaint () {
      if (italicPaint == null) {
        italicPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | paintFlags);
        if (italicTypeface != null) {
          italicPaint.setTypeface(italicTypeface);
        } else {
          italicPaint.setTypeface(regularTypeface);
          italicPaint.setTextSkewX(TEXT_SKEW_ITALIC);
        }
      }
      return italicPaint;
    }

    public TextPaint getBoldPaint () {
      if (boldPaint == null) {
        if (boldTypeface != null) {
          boldPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | paintFlags);
          boldPaint.setTypeface(boldTypeface);
        } else {
          boldPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | Paint.FAKE_BOLD_TEXT_FLAG | paintFlags);
          boldPaint.setFakeBoldText(true);
          boldPaint.setTypeface(regularTypeface);
        }
      }
      return boldPaint;
    }

    public TextPaint getFakeBoldPaint () {
      if (boldTypeface == null) {
        return getBoldPaint();
      }
      if (fakeBoldPaint == null) {
        fakeBoldPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | Paint.FAKE_BOLD_TEXT_FLAG | paintFlags);
        fakeBoldPaint.setFakeBoldText(true);
        fakeBoldPaint.setTypeface(regularTypeface);
      }
      return fakeBoldPaint;
    }

    public TextPaint getBoldItalicPaint () {
      if (boldItalicPaint == null) {
        if (boldItalicTypeface != null) {
          boldItalicPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | paintFlags);
          boldItalicPaint.setTypeface(boldItalicTypeface);
        } else if (italicTypeface != null) { // italic is higher priority because text skew looks bad
          boldItalicPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | Paint.FAKE_BOLD_TEXT_FLAG | paintFlags);
          boldItalicPaint.setTypeface(italicTypeface);
          boldItalicPaint.setFakeBoldText(true);
        } else if (boldTypeface != null) {
          boldItalicPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | paintFlags);
          boldItalicPaint.setTypeface(boldTypeface);
          boldItalicPaint.setTextSkewX(TEXT_SKEW_ITALIC);
        } else {
          boldItalicPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | Paint.FAKE_BOLD_TEXT_FLAG | paintFlags);
          boldItalicPaint.setTypeface(regularTypeface);
          boldItalicPaint.setTextSkewX(TEXT_SKEW_ITALIC);
          boldItalicPaint.setFakeBoldText(true);
        }
      }
      return boldItalicPaint;
    }

    public TextPaintStorage getMonospaceStorage () {
      if (monospaceTypeface != null) {
        if (monospaceStorage == null) {
          monospaceStorage = new TextPaintStorage(monospaceTypeface, paintFlags);
        }
        return monospaceStorage;
      }
      return this;
    }

    public TextPaintStorage getUnderlineStorage () {
      return underlineStorage != null ? underlineStorage : (underlineStorage = newStorage(Paint.UNDERLINE_TEXT_FLAG));
    }

    public TextPaintStorage getStrikeThroughStorage () {
      return strikeThroughStorage != null ? strikeThroughStorage : (strikeThroughStorage = newStorage(Paint.STRIKE_THRU_TEXT_FLAG));
    }

    private TextPaintStorage newStorage (int addFlags) {
      return new TextPaintStorage(regularTypeface, boldTypeface, italicTypeface, boldItalicTypeface, monospaceTypeface, paintFlags | addFlags);
    }
  }

  public static TextPaintStorage newRobotoStorage () {
    return new TextPaintStorage(getRobotoRegular(), getRobotoMedium(), getRobotoItalic(), null, getRobotoMono(), 0);
  }
}