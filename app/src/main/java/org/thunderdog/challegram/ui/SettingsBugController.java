package org.thunderdog.challegram.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.SystemClock;
import android.util.SparseIntArray;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.IdRes;
import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.drinkmore.Tracer;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.MainActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Background;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.DoubleHeaderView;
import org.thunderdog.challegram.navigation.SettingsWrap;
import org.thunderdog.challegram.navigation.SettingsWrapBuilder;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccount;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.unsorted.Test;
import org.thunderdog.challegram.util.StringList;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.BetterChatView;
import org.thunderdog.challegram.widget.MaterialEditTextGroup;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.IntList;
import me.vkryl.core.lambda.RunnableBool;
import me.vkryl.core.unit.ByteUnit;

/**
 * Date: 06/03/2017
 * Author: default
 */

public class SettingsBugController extends RecyclerViewController<SettingsBugController.Args> implements
  View.OnClickListener,
  ViewController.SettingsIntDelegate,
  View.OnLongClickListener,
  Log.OutputListener {
  public static final int SECTION_MAIN = 0;
  public static final int SECTION_UTILITIES = 1;
  public static final int SECTION_TDLIB = 2;
  public static final int SECTION_ERROR = 3;

  public static class Args {
    public final int section;
    public final Settings.CrashInfo crash;
    private int testerLevel = Tdlib.TESTER_LEVEL_NONE;
    private boolean mainCrash;

    public Args (int section) {
      this(section, null);
    }

    public Args (Settings.CrashInfo crash) {
      this.crash = crash;
      this.mainCrash = true;
      if (crash.getType() == Settings.CrashType.TDLIB) {
        this.section = SECTION_TDLIB;
      } else {
        this.section = SECTION_ERROR;
      }
    }

    public Args (int section, Settings.CrashInfo crash) {
      this.section = section;
      this.crash = crash;
    }

    public Args setTesterLevel (int level) {
      this.testerLevel = level;
      return this;
    }
  }

  public SettingsBugController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  private int section = SECTION_MAIN;
  private int testerLevel = Tdlib.TESTER_LEVEL_NONE;
  private Settings.CrashInfo crash;
  private boolean isMainCrash;

  @Override
  public void setArguments (Args args) {
    super.setArguments(args);
    this.section = args != null ? args.section : SECTION_MAIN;
    this.testerLevel = args != null ? args.testerLevel : Tdlib.TESTER_LEVEL_NONE;
    this.crash = args != null ? args.crash : null;
    this.isMainCrash = args != null && args.mainCrash;
  }

  @Override
  public int getId () {
    return R.id.controller_bug_killer;
  }

  @Override
  public CharSequence getName () {
    if (isMainCrash) {
      return Lang.getString(R.string.LaunchTitle);
    }
    switch (section) {
      case SECTION_MAIN:
        return BuildConfig.VERSION_NAME;
      case SECTION_UTILITIES:
        return Lang.getString(R.string.TestMode);
      case SECTION_TDLIB: {
        return "TDLib " + getTdlibVersionSignature(false);
      }
    }
    throw new AssertionError(section);
  }

  @Override
  protected boolean needPersistentScrollPosition () {
    return true;
  }

  private String getTdlibVersionSignature (boolean needBuildNo) {
    String signature = tdlib != null ? tdlib.tdlibVersionSignature() : null;
    return (signature != null ? signature : "???") + (needBuildNo ? "." + BuildConfig.ORIGINAL_VERSION_CODE : "");
  }

  private SettingsAdapter adapter;

  private static String getLogVerbosity (int level) {
    switch (level) {
      case Settings.TDLIB_LOG_VERBOSITY_UNKNOWN: return "-1";
      case 0: return "ASSERT";
      case 1: return "ERROR";
      case 2: return "WARNING";
      case 3: return "INFO";
      case 4: return "DEBUG";
      case 5: return "VERBOSE";
    }
    return "MORE:" + level;
  }

  private final long[] logSize = new long[2];

  private Runnable scheduledCheck;

  @Override
  protected void onFocusStateChanged () {
    super.onFocusStateChanged();
    if (section == SECTION_TDLIB) {
      if (isFocused()) {
        UI.post(new Runnable() {
          @Override
          public void run () {
            if (isFocused()) {
              File file;
              file = new File(TdlibManager.getLogFilePath(false));
              setLogSize(file.length(), false);
              file = new File(TdlibManager.getLogFilePath(true));
              setLogSize(file.length(), true);
              UI.post(this, 1500);
            }
          }
        }, 1500);
      } else if (scheduledCheck != null) {
        UI.removePendingRunnable(scheduledCheck);
        scheduledCheck = null;
      }
    }
  }

  private void setLogSize (long size, boolean old) {
    final int i = old ? 1 : 0;
    if (this.logSize[i] != size) {
      this.logSize[i] = size;
      if (adapter != null) {
        adapter.updateValuedSettingById(old ? R.id.btn_tdlib_viewLogsOld : R.id.btn_tdlib_viewLogs);
      }
    }
  }

  private void checkLogSize (boolean old) {
    try {
      setLogSize(new java.io.File(TdlibManager.getLogFilePath(old)).length(), old);
    } catch (Throwable ignored) { }
  }

  private Log.LogFiles logFiles;
  private boolean filesLoaded;

  private void setLogFiles (Log.LogFiles files) {
    this.logFiles = files;
    this.filesLoaded = true;
    adapter.updateValuedSettingById(R.id.btn_log_files);
  }

  private long lastLoadLog;

  private boolean needsAppLogs () {
    return lastLoadLog == 0 || (filesLoaded && (logFiles == null || logFiles.isEmpty() || SystemClock.elapsedRealtime() - lastLoadLog >= 1000l));
  }

  private void getLogFiles () {
    if (needsAppLogs()) {
      lastLoadLog = SystemClock.elapsedRealtime();
      Log.getLogFiles(result -> UI.post(() -> {
        if (!isDestroyed()) {
          setLogFiles(result);
        }
      }));
    }
  }

  private boolean isDeleting;

  private void setIsDeleting (boolean isDeleting) {
    if (this.isDeleting != isDeleting) {
      this.isDeleting = isDeleting;
      adapter.updateValuedSettingById(R.id.btn_log_files);
    }
  }

  private void deleteAllFiles () {
    if (filesLoaded && logFiles != null && !logFiles.isEmpty() && !isDeleting) {
      setIsDeleting(true);
      Log.deleteAll(logFiles, result -> {
        if (!isDestroyed()) {
          UI.post(() -> {
            if (!isDestroyed()) {
              setLogFiles(logFiles);
              setIsDeleting(false);
            }
          });
        }
      }, null);
    }
  }

  @Override
  public void onLogOutput (int tag, int level, String message, @Nullable Throwable t) {
    if (level <= Log.LEVEL_WARNING || needsAppLogs()) {
      UI.post(() -> {
        if (!isDestroyed()) {
          getLogFiles();
        }
      });
    }
  }

  @Override
  public void onLogFilesAltered () {
    UI.post(() -> {
      if (!isDestroyed()) {
        getLogFiles();
      }
    });
  }

  private String getDiskAvailableInfo () {
    return Strings.buildSize(U.getAvailableInternalMemorySize());
  }

  private String getCrashName () {
    switch (crash.getType()) {
      case Settings.CrashType.EXTERNAL_ERROR:
        return Lang.getString(R.string.LaunchSubtitleExternalError);
      case Settings.CrashType.DISK_FULL:
        return Lang.getString(R.string.LaunchSubtitleDiskFull);
      case Settings.CrashType.TDLIB:
        return Lang.getString(R.string.LaunchSubtitleTdlibIssue, getTdlibVersionSignature(true));
      case Settings.CrashType.DATABASE_BROKEN:
        return Lang.getString(R.string.LaunchSubtitleDatabaseBroken);
    }
    return null;
  }

  private CharSequence getCrashGuide () {
    int resId;
    switch (crash.getType()) {
      case Settings.CrashType.EXTERNAL_ERROR:
        resId = R.string.LaunchAppGuideExternalError;
        break;
      case Settings.CrashType.DISK_FULL:
        resId = R.string.LaunchAppGuideDiskFull;
        break;
      case Settings.CrashType.TDLIB:
        resId = R.string.LaunchAppGuideTdlibIssue;
        break;
      case Settings.CrashType.DATABASE_BROKEN:
        resId = R.string.LaunchAppGuideDatabaseBroken;
        break;
      default:
        return null;
    }
    return Lang.getMarkdownStringSecure(this, resId, getDiskAvailableInfo(), U.getMarketUrl());
  }

  @Override
  protected int getBackButton () {
    return isMainCrash ? BackHeaderButton.TYPE_CLOSE : super.getBackButton();
  }

  private DoubleHeaderView customHeaderCell;

  @Override
  public View getCustomHeaderCell () {
    return customHeaderCell;
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    checkLogSize(false);
    checkLogSize(true);

    if (isMainCrash) {
      customHeaderCell = new DoubleHeaderView(context);
      customHeaderCell.setThemedTextColor(this);
      customHeaderCell.initWithMargin(Screen.dp(18f), true);
      customHeaderCell.setTitle(getName());
      customHeaderCell.setSubtitle(getCrashName());
    }

    adapter = new SettingsAdapter(this) {
      @Override
      protected void setChatData (ListItem item, int position, BetterChatView chatView) {
        chatView.setEnabled(false);
        TdlibAccount account = (TdlibAccount) item.getData();
        chatView.setTitle(account.getName());
        chatView.setAvatar(account.getAvatarFile(false), account.getAvatarPlaceholderMetadata());
        chatView.setSubtitle(Lang.getString(R.string.LaunchAppUserSubtitle));
      }

      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        if (isMainCrash) {
          int colorId;
          switch (item.getId()) {
            case R.id.btn_launchApp:
              colorId = R.id.theme_color_iconActive;
              break;
            /*case R.id.btn_shareError:
            case R.id.btn_showError:*/
            case R.id.btn_eraseDatabase:
              colorId = R.id.theme_color_iconNegative;
              break;
            default:
              colorId = 0;
              break;
          }
          view.setIconColorId(colorId);
        }
        switch (item.getId()) {
          case R.id.btn_log_verbosity: {
            final boolean isCapturing = Log.isCapturing();
            if (isUpdate) {
              view.setEnabledAnimated(!isCapturing);
            } else {
              view.setEnabled(!isCapturing);
            }
            view.setData(getLogVerbosity(isCapturing ? Log.LEVEL_VERBOSE : Log.getLogLevel()));
            break;
          }
          case R.id.btn_secret_replacePhoneNumber: {
            view.getToggler().setRadioEnabled(Settings.instance().needHidePhoneNumber(), isUpdate);
            break;
          }
          case R.id.btn_secret_disableNetwork: {
            view.getToggler().setRadioEnabled(Settings.instance().forceDisableNetwork(), isUpdate);
            break;
          }
          case R.id.btn_secret_forceTcpInCalls: {
            view.getToggler().setRadioEnabled(Settings.instance().forceTcpInCalls(), isUpdate);
            break;
          }
          case R.id.btn_secret_forceTdlibRestarts: {
            view.getToggler().setRadioEnabled(Settings.instance().forceTdlibRestart(), isUpdate);
            break;
          }
          case R.id.btn_switchRtl: {
            view.getToggler().setRadioEnabled(Lang.rtl(), isUpdate);
            break;
          }
          case R.id.btn_secret_dontReadMessages: {
            view.getToggler().setRadioEnabled(Settings.instance().dontReadMessages(), isUpdate);
            break;
          }
          case R.id.btn_log_files: {
            final boolean isEnabled = !Log.isCapturing() && filesLoaded && !isDeleting && logFiles != null && !logFiles.isEmpty();
            if (isUpdate) {
              view.setEnabledAnimated(isEnabled);
            } else {
              view.setEnabled(isEnabled);
            }
            if (filesLoaded) {
              if (logFiles == null || logFiles.isEmpty()) {
                view.setData(Lang.plural(R.string.xFiles, 0));
              } else {
                StringBuilder b = new StringBuilder();
                b.append(Strings.buildSize(logFiles.totalSize));
                if (logFiles.logsCount > 0) {
                  if (b.length() > 0) {
                    b.append(", ");
                  }
                  b.append(logFiles.logsCount);
                  b.append(" log");
                  if (logFiles.logsCount != 1) {
                    b.append('s');
                  }
                }
                if (logFiles.crashesCount > 0) {
                  if (b.length() > 0) {
                    b.append(", ");
                  }
                  b.append(logFiles.crashesCount);
                  b.append(" crash");
                  if (logFiles.crashesCount != 1) {
                    b.append("es");
                  }
                }
                view.setData(b.toString());
              }
            } else {
              view.setData(R.string.LoadingInformation);
            }
            break;
          }
          case R.id.btn_log_tags: {
            final boolean isCapturing = Log.isCapturing();
            if (isUpdate) {
              view.setEnabledAnimated(!isCapturing);
            } else {
              view.setEnabled(!isCapturing);
            }
            StringBuilder b = new StringBuilder();
            for (int tag : Log.TAGS) {
              if (Log.isEnabled(tag)) {
                if (b.length() > 0) {
                  b.append(", ");
                }
                b.append(Log.getLogTag(tag));
              }
            }
            if (b.length() == 0) {
              b.append("None");
            }
            view.setData(b.toString());
            break;
          }
          case R.id.btn_log_android: {
            view.getToggler().setRadioEnabled(Log.checkSetting(Log.SETTING_ANDROID_LOG), false);
            break;
          }

          case R.id.btn_tdlib_verbosity: {
            String module = (String) item.getData();
            Settings.TdlibLogSettings settings = Settings.instance().getLogSettings();
            int verbosity = settings.getVerbosity(module);
            if (module != null && verbosity == settings.getDefaultVerbosity(module)) {
              view.setData("Default");
              view.getToggler().setRadioEnabled(verbosity <= settings.getVerbosity(null), isUpdate);
            } else {
              view.setData(getLogVerbosity(verbosity));
              view.getToggler().setRadioEnabled(module != null ? verbosity <= settings.getVerbosity(null) : verbosity > 0, isUpdate);
            }
            break;
          }
          case R.id.btn_tdlib_logSize: {
            view.setData(Strings.buildSize(Settings.instance().getLogSettings().getLogMaxFileSize()));
            break;
          }
          case R.id.btn_tdlib_viewLogs: {
            view.setData(Strings.buildSize(logSize[0]));
            break;
          }
          case R.id.btn_tdlib_viewLogsOld: {
            view.setData(Strings.buildSize(logSize[1]));
            break;
          }
          case R.id.btn_tdlib_androidLogs: {
            view.getToggler().setRadioEnabled(Settings.instance().getLogSettings().needAndroidLog(), isUpdate);
            break;
          }
        }
      }
    };
    adapter.setOnLongClickListener(this);

    ArrayList<ListItem> items = new ArrayList<>();

    if (isMainCrash) {
      if (crash.accountId != TdlibAccount.NO_ID) {
        TdlibAccount account = TdlibManager.instanceForAccountId(crash.accountId).account(crash.accountId);
        if (account.getDisplayInformation() != null) {
          items.add(new ListItem(ListItem.TYPE_CHAT_BETTER).setData(account));
          items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        }
      }

      if (crash.getType() == Settings.CrashType.TDLIB && !StringUtils.isEmpty(crash.message)) {
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, crash.message, false));
      }

      if (!items.isEmpty())
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_launchApp, R.drawable.baseline_warning_24, R.string.LaunchApp).setTextColorId(R.id.theme_color_textNeutral));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_update, R.drawable.baseline_system_update_24, R.string.LaunchAppCheckUpdate));
      if (section != SECTION_TDLIB) {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        if (crash.getType() == Settings.CrashType.DISK_FULL) {
          items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_showError, R.drawable.baseline_info_24, R.string.LaunchAppViewError)/*.setTextColorId(R.id.theme_color_textNegative)*/);
        } else {
          items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_shareError, R.drawable.baseline_share_24, R.string.LaunchAppShareError)/*.setTextColorId(R.id.theme_color_textNegative)*/);
        }
      }
      switch (crash.getType()) {
        case Settings.CrashType.DATABASE_BROKEN:
        case Settings.CrashType.TDLIB: {
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
          items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_eraseDatabase, R.drawable.baseline_delete_forever_24, R.string.LaunchAppEraseDatabase).setTextColorId(R.id.theme_color_textNegative));
          break;
        }
      }
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, getCrashGuide(), false));
    }

    switch (section) {
      case SECTION_ERROR: {
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_tdlib, 0, R.string.TdlibLogs, false));
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_appLogs, 0, R.string.AppLogs, false));
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_testingUtils, 0, R.string.TestMode, false));
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        break;
      }
      case SECTION_MAIN: {
        if (items.isEmpty())
          items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
        items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.AppLogs, false));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_log_verbosity, 0, R.string.DebugVerbosity, false));
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_log_tags, 0, R.string.DebugLogTags, false));
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_log_files, 0, R.string.DebugLogFiles, false));
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_log_android, 0, R.string.DebugLogcat, false));
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, Lang.getMarkdownStringSecure(this, R.string.DebugAppLogsInfo), false));

        if (crash == null) {
          items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Other));
          items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
          items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_tdlib, 0, R.string.TdlibLogs, false));
          items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        }
        break;
      }
      case SECTION_UTILITIES: {
        if (!items.isEmpty()) {
          items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        }
        int initialSize = items.size();
        if (tdlib != null && !tdlib.context().inRecoveryMode()) {
          // items.add(new SettingItem(SettingItem.TYPE_SEPARATOR_FULL));
          items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_secret_readAllChats, 0, R.string.ReadAllChats, false));
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
          items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_secret_tdlibDatabaseStats, 0, "TDLib database statistics", false));
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
          items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_secret_databaseStats, 0, "Other internal statistics", false));

          if (testerLevel >= Tdlib.TESTER_LEVEL_ADMIN) {
            items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
            items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_secret_stressTest, 0, "Stress test TDLib restarts", false));
          }
          if (testerLevel >= Tdlib.TESTER_LEVEL_ADMIN || Settings.instance().forceTdlibRestart()) {
            if (items.size() > initialSize)
              items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
            items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_secret_forceTdlibRestarts, 0, "Force TDLib restarts", Settings.instance().forceTdlibRestart()));
          }

          if (testerLevel >= Tdlib.TESTER_LEVEL_DEVELOPER) {
            if (tdlib.isAuthorized()) {
              items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
              items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_secret_sendAllChangeLogs, 0, "Send all change logs", false));
            }
          }

          if (testerLevel >= Tdlib.TESTER_LEVEL_CREATOR) {
            items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
            items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_secret_copyLanguageCodes, 0, "Copy language codes list", false));
          }

          TdApi.User user = tdlib.myUser();
          if (user != null && user.profilePhoto != null) {
            items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
            items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_secret_deleteProfilePhoto, 0, "Delete profile photo from cache", false));
          }
        }

        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_secret_dropSavedScrollPositions, 0, "Drop saved scroll positions", false));

        if (testerLevel >= Tdlib.TESTER_LEVEL_CREATOR) {
          if (items.size() > initialSize)
            items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
          items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_secret_dontReadMessages, 0, "Don't read messages", false));
        }

        if (items.size() > initialSize)
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_secret_resetTutorials, 0, "Reset tutorials", false));

        if (tdlib != null) {
          if (items.size() > initialSize)
            items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
          items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_secret_resetLocalNotificationSettings, 0, "Reset local notification settings", false));
        }

        if (tdlib != null) {
          if (items.size() > initialSize)
            items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
          items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_secret_dropHidden, 0, "Drop hidden notification identifiers", false));
        }

        if (testerLevel >= Tdlib.TESTER_LEVEL_READER || Settings.instance().needHidePhoneNumber()) {
          if (items.size() > initialSize)
            items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
          items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_secret_replacePhoneNumber, 0, "Hide phone number in drawer", Settings.instance().needHidePhoneNumber()));
        }
        if (testerLevel >= Tdlib.TESTER_LEVEL_READER || Settings.instance().forceTcpInCalls()) {
          if (items.size() > initialSize)
            items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
          items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_secret_forceTcpInCalls, 0, "Force TCP in calls", Settings.instance().forceTcpInCalls()));
        }
        if (testerLevel >= Tdlib.TESTER_LEVEL_ADMIN || Settings.instance().forceDisableNetwork()) {
          if (items.size() > initialSize)
            items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
          items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_secret_disableNetwork, 0, "Force disable network", Settings.instance().forceDisableNetwork()));
        }

        /*if (Config.RTL_BETA) {
          items.add(new SettingItem(SettingItem.TYPE_SEPARATOR_FULL));
          items.add(new SettingItem(SettingItem.TYPE_RADIO_SETTING, R.id.btn_switchRtl, 0, R.string.RtlLayout, false));
          items.add(new SettingItem(SettingItem.TYPE_SEPARATOR_FULL));
          items.add(new SettingItem(SettingItem.TYPE_SETTING, R.id.btn_debugSwitchRtl, 0, "Add / Remove RTL switcher", false));
        }*/

        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, "Tests (crash when failed)", false));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_test_database, 0, "Test database", false));
        if (testerLevel >= Tdlib.TESTER_LEVEL_ADMIN) {
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
          items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_test_recovery_tdlib, 0, "Crash & enter recovery mode (TDLib error)", false));
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
          items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_test_recovery_tdlib, 0, "Crash & enter recovery mode (disk full)", false).setStringValue("database or disk is full"));
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
          items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_test_recovery_tdlib, 0, "Crash & enter recovery mode (database broken)", false).setStringValue("Wrong key or database is corrupted"));
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
          items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_test_recovery_tdlib, 0, "Crash & enter recovery mode (other external error)", false).setStringValue("I/O error"));
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
          items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_test_crash1, 0, "Crash app (method 1, indirect)", false));
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
          items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_test_crash2, 0, "Crash app (method 2, direct)", false));
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
          items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_test_crash3, 0, "Crash app (method 3, native indirect)", false));
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
          items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_test_crash4, 0, "Crash app (method 4, native direct)", false));
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
          items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_test_crashDirect, 0, "Crash app (default)", false));
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
          items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_test_crashDirectNative, 0, "Crash app (native)", false));
        }
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        break;
      }
      case SECTION_TDLIB: {
        if (items.isEmpty())
          items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));

        if (isMainCrash) {
          items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
          items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_shareError, R.drawable.baseline_share_24, R.string.LaunchAppShareError).setTextColorId(R.id.theme_color_textNegative));
          items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        }

        items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, /*section == SECTION_TON ? R.string.TonLogs :*/ R.string.TdlibLogs, false));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_TOGGLER, R.id.btn_tdlib_verbosity, 0, R.string.DebugVerbosity, false));
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_tdlib_logSize, 0, R.string.DebugLogSize, false));
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_tdlib_viewLogs, 0, TdlibManager.getLogFile(false).getName(), false));
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_tdlib_viewLogsOld, 0, TdlibManager.getLogFile(true).getName(), false));
        Settings.TdlibLogSettings settings = Settings.instance().getLogSettings();
        if (testerLevel >= Tdlib.TESTER_LEVEL_DEVELOPER || settings.needAndroidLog()) {
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
          items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tdlib_androidLogs, 0, R.string.DebugLogcatOnly, false));
        }
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_tdlib_resetLogSettings, 0, R.string.DebugReset, false));

        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

        List<String> modules = settings.getModules();
        if (modules != null && !modules.isEmpty()) {
          items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.DebugModules, false));
          boolean first = true;
          for (String module : modules) {
            if (first) {
              items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
              first = false;
            } else {
              items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
            }
            items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_TOGGLER, R.id.btn_tdlib_verbosity, 0, module, false).setData(module));
          }
          items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
          items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, Lang.getMarkdownStringSecure(this, R.string.DebugModulesInfo), false));
        }

        if (isMainCrash) {
          items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
          items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_testingUtils, 0, R.string.TestMode, false));
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
          items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_appLogs, 0, R.string.AppLogs, false));
          items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        }

        break;
      }
    }

    adapter.setItems(items, false);

    switch (section) {
      case SECTION_MAIN: {
        if (tdlib != null) {
          getLogFiles();
          Log.addOutputListener(this);

          if (crash == null) {
            tdlib.getTesterLevel(level -> {
              this.testerLevel = level;
              tdlib.uiExecute(() -> {
                if (!isDestroyed()) {
                  int i = adapter.indexOfViewById(/*Config.NEED_TON ? R.id.btn_ton :*/ R.id.btn_tdlib);
                  if (i == -1)
                    return;
                  if (level > Tdlib.TESTER_LEVEL_NONE) {
                    adapter.getItems().add(i + 1, new ListItem(ListItem.TYPE_SEPARATOR_FULL));
                    adapter.getItems().add(i + 2, new ListItem(ListItem.TYPE_SETTING, R.id.btn_testingUtils, 0, R.string.TestMode, false));
                    adapter.notifyItemRangeInserted(i + 1, 2);
                    i += 2;
                    if (level == Tdlib.TESTER_LEVEL_READER) {
                      adapter.addItem(i + 2, new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, Strings.buildMarkdown(this, "To unlock more Testing Utilities you have to be a member of @tgandroidtests.", null), false));
                    }
                  } else {
                    adapter.addItem(i + 2, new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, Strings.buildMarkdown(this, "To unlock Testing Utilities you have to be subscribed to @tgx_android or be a member of @tgandroidtests.", null), false));
                  }
                }
              });
            });
          }
        }
        break;
      }
    }

    recyclerView.setAdapter(adapter);
  }

  @Override
  public void destroy () {
    super.destroy();
    Log.removeOutputListener(this);
  }


  private static final int TEST_DATABASE = 1;
  private int runningTest;
  private void runTest (int test, boolean needPrompt) {
    if (runningTest != 0)
      return;
    if (needPrompt) {
      showWarning("Test may take some time. Don't be scared if it crashes.\n\nWarning: don't do anything in the app while test is running.", confirm -> {
        if (confirm) {
          runTest(test, false);
        }
      });
      return;
    }
    setStackLocked(true);
    Runnable after = () -> {
      if (!isDestroyed()) {
        setStackLocked(false);
      }
      UI.showToast("Test completed successfully", Toast.LENGTH_SHORT);
    };
    runningTest = test;
    switch (test) {
      case TEST_DATABASE:
        runDbTests(after);
        break;
      default:
        runningTest = 0;
        break;
    }
  }

  private void runDbTests (Runnable after) {
    UI.showToast("Running tests, please do nothing and wait...", Toast.LENGTH_SHORT);
    Background.instance().post(() -> {
      try {
        Test.testLevelDB();
      } catch (Error | RuntimeException e) {
        throw e;
      } catch (Throwable e) {
        throw new AssertionError(e);
      }
      UI.post(after);
    });
  }

  private boolean isErasingData;

  @Override
  public void onClick (View v) {
    final int viewId = v.getId();
    switch (viewId) {
      case R.id.btn_launchApp: {
        ((MainActivity) context).proceedFromRecovery();
        break;
      }
      case R.id.btn_update: {
        Intents.openSelfGooglePlay();
        break;
      }
      case R.id.btn_shareError: {
        Intents.shareText(getCrashName() + "\n" + crash.message);
        break;
      }
      case R.id.btn_eraseDatabase: {
        if (isErasingData)
          return;
        if (crash.accountId != TdlibAccount.NO_ID) {
          tdlib.context().tdlib(crash.accountId).ui().eraseLocalData(this, false, new TdlibUi.EraseCallback() {
            @Override
            public void onPrepareEraseData() {
              isErasingData = true;
            }

            @Override
            public void onEraseDataCompleted() {
              isErasingData = false;
              ((MainActivity) context).proceedFromRecovery();
            }
          });
        } else {
          ((MainActivity) context()).batchPerformFor(Lang.getMarkdownString(this, R.string.EraseDatabaseWarn), Lang.getString(R.string.EraseConfirm), accounts -> {
            showWarning(Lang.getMarkdownString(this, R.string.EraseDatabaseWarn2), success -> {
              if (success && !isDestroyed() && isFocused() && navigationController() != null) {
                UI.showToast(R.string.EraseDatabaseProgress, Toast.LENGTH_SHORT);
                navigationController().getStack().setIsLocked(true);

                isErasingData = true;
                AtomicBoolean error = new AtomicBoolean();
                accounts.remove(accounts.size() - 1).tdlib().eraseTdlibDatabase(new RunnableBool() {
                  @Override
                  public void runWithBool (boolean arg) {
                    if (!arg) {
                      error.set(true);
                    }
                    if (!accounts.isEmpty()) {
                      accounts.remove(accounts.size() - 1).tdlib().eraseTdlibDatabase(this);
                      return;
                    }
                    isErasingData = false;
                    if (!isDestroyed() && navigationController() != null) {
                      navigationController().getStack().setIsLocked(false);
                      if (!error.get()) {
                        ((MainActivity) context).proceedFromRecovery();
                        UI.showToast(R.string.EraseDatabaseDone, Toast.LENGTH_SHORT);
                      } else {
                        UI.showToast(R.string.EraseDatabaseError, Toast.LENGTH_SHORT);
                      }
                    }
                  }
                });
              }
            });
          });
        }
        break;
      }
      case R.id.btn_showError: {
        TextController c = new TextController(context, tdlib);
        c.setArguments(TextController.Arguments.fromRawText(getCrashName(), crash.message, "text/plain"));
        navigateTo(c);
        break;
      }
      case R.id.btn_switchRtl: {
        Settings.instance().setNeedRtl(Lang.packId(), adapter.toggleView(v));
        break;
      }
      case R.id.btn_debugSwitchRtl: {
        context.addRemoveRtlSwitch();
        break;
      }
      case R.id.btn_secret_replacePhoneNumber: {
        Settings.instance().setHidePhoneNumber(adapter.toggleView(v));
        break;
      }
      case R.id.btn_secret_disableNetwork: {
        Settings.instance().setDisableNetwork(adapter.toggleView(v));
        TdlibManager.instance().watchDog().letsHelpDoge();
        break;
      }
      case R.id.btn_secret_forceTdlibRestarts: {
        TdlibManager.instance().setForceTdlibRestarts(adapter.toggleView(v));
        break;
      }
      case R.id.btn_secret_forceTcpInCalls: {
        Settings.instance().setForceTcpInCalls(adapter.toggleView(v));
        break;
      }
      case R.id.btn_test_database: {
        runTest(TEST_DATABASE, true);
        break;
      }
      case R.id.btn_test_recovery_tdlib: {
        String text = ((ListItem) v.getTag()).getStringValue();
        if (StringUtils.isEmpty(text)) {
          text = "some tdlib bug";
        }
        Settings.instance().storeTdlibTestCrash(tdlib.id(), text);
        System.exit(0);
        break;
      }
      case R.id.btn_test_crash1: {
        Tracer.test1("[SUCCESS] INDIRECT " + MathUtils.random(0, 10000));
        break;
      }
      case R.id.btn_test_crash2: {
        Tracer.test2("[SUCCESS] DIRECT " + -MathUtils.random(0, 10000));
        break;
      }
      case R.id.btn_test_crash3: {
        Tracer.test3("[SUCCESS] INDIRECT NATIVE " + MathUtils.random(0, 10000));
        break;
      }
      case R.id.btn_test_crash4: {
        Tracer.test4("[SUCCESS] DIRECT NATIVE " + -MathUtils.random(0, 10000));
        break;
      }
      case R.id.btn_test_crashDirectNative: {
        Tracer.test5("[SUCCESS] DIRECT THROW " + -MathUtils.random(0, 10000));
        break;
      }
      case R.id.btn_test_crashDirect: {
        throw new RuntimeException("This is a default test");
      }
      case R.id.btn_secret_dropHidden: {
        tdlib.notifications().onDropNotificationData(false);
        break;
      }
      // case R.id.btn_ton:
      case R.id.btn_tdlib: {
        openTdlibLogs(testerLevel, crash);
        break;
      }
      case R.id.btn_appLogs: {
        SettingsBugController c = new SettingsBugController(context, tdlib);
        c.setArguments(new Args(SECTION_MAIN, crash).setTesterLevel(testerLevel));
        navigateTo(c);
        break;
      }
      case R.id.btn_testingUtils: {
        RunnableBool callback = proceed -> {
          if (proceed) {
            SettingsBugController c = new SettingsBugController(context, tdlib);
            c.setArguments(new Args(SECTION_UTILITIES, crash).setTesterLevel(testerLevel));
            navigateTo(c);
          }
        };
        if (crash != null) {
          callback.runWithBool(true);
        } else {
          showWarning(Lang.getMarkdownString(this, R.string.TestModeWarn), callback);
        }
        break;
      }
      case R.id.btn_secret_resetTutorials: {
        Settings.instance().resetTutorials();
        UI.showToast("Hints reset completed", Toast.LENGTH_SHORT);
        break;
      }
      case R.id.btn_log_verbosity: {
        ListItem[] items = new ListItem[Log.LEVEL_VERBOSE + 1];
        final int logLevel = Log.isCapturing() ? Log.LEVEL_VERBOSE : Log.getLogLevel();
        for (int level = 0; level < items.length; level++) {
          items[level] = new ListItem(ListItem.TYPE_RADIO_OPTION, level + 1, 0, getLogVerbosity(level), R.id.btn_log_verbosity, level == logLevel);
        }
        showSettings(R.id.btn_log_verbosity, items, this, false);
        break;
      }
      case R.id.btn_log_files: {
        SettingsLogFilesController c = new SettingsLogFilesController(context, tdlib);
        c.setArguments(new SettingsLogFilesController.Arguments(logFiles));
        navigateTo(c);
        break;
      }
      case R.id.btn_log_android: {
        Log.setSetting(Log.SETTING_ANDROID_LOG, ((SettingView) v).getToggler().toggle(true));
        break;
      }
      case R.id.btn_log_tags: {
        ListItem[] items = new ListItem[Log.TAGS.length];
        for (int i = 0; i < items.length; i++) {
          int tag = Log.TAGS[i];
          items[i] = new ListItem(ListItem.TYPE_CHECKBOX_OPTION, tag, 0, "[" + Log.getLogTag(tag) + "]: " + Log.getLogTagDescription(tag), Log.isEnabled(tag));
        }
        showSettings(R.id.btn_log_tags, items, this, true);
        break;
      }
      case R.id.btn_secret_dontReadMessages: {
        int i = adapter.indexOfViewById(R.id.btn_secret_dontReadMessages);
        if (i != -1) {
          boolean newValue = !Settings.instance().dontReadMessages();
          Settings.instance().setDontReadMessages(newValue);
          if (newValue != Settings.instance().dontReadMessages()) {
            UI.showToast("You can't enable that", Toast.LENGTH_SHORT);
          } else {
            adapter.updateValuedSettingById(R.id.btn_secret_dontReadMessages);
          }
        }
        break;
      }
      case R.id.btn_secret_stressTest: {
        openInputAlert("Stress test", "Restart count", R.string.Done, R.string.Cancel, "50", new InputAlertCallback() {
          @Override
          public boolean onAcceptInput (MaterialEditTextGroup inputView, String result) {
            if (!StringUtils.isNumeric(result))
              return false;
            int count = StringUtils.parseInt(result);
            if (count <= 0)
              return false;
            if (!isDestroyed()) {
              if (navigationController != null)
                navigationController.getStack().destroyAllExceptLast();
              tdlib.stressTest(count);
              return true;
            }
            return false;
          }
        }, true);
        break;
      }
      case R.id.btn_secret_copyLanguageCodes: {
        tdlib.client().send(new TdApi.GetLocalizationTargetInfo(false), result -> {
          if (result instanceof TdApi.LocalizationTargetInfo) {
            TdApi.LocalizationTargetInfo info = (TdApi.LocalizationTargetInfo) result;
            StringBuilder codes = new StringBuilder();
            for (TdApi.LanguagePackInfo languagePackInfo : info.languagePacks) {
              if (!languagePackInfo.isBeta && languagePackInfo.isOfficial) {
                if (codes.length() > 0)
                  codes.append(", ");
                codes.append("'");
                codes.append(languagePackInfo.id);
                codes.append("'");
              }
            }
            UI.copyText(codes.toString(), R.string.CopiedText);
          }
        });
        break;
      }
      case R.id.btn_secret_deleteContacts: {
        tdlib.contacts().reset(true, () -> {
          UI.showToast("Contacts reset done", Toast.LENGTH_SHORT);
          tdlib.ui().post(() -> tdlib.contacts().startSyncIfNeeded(context(), false, null));
        });
        break;
      }
      case R.id.btn_secret_resetLocalNotificationSettings: {
        tdlib.notifications().resetNotificationSettings(true);
        break;
      }
      case R.id.btn_secret_databaseStats: {
        String stats = Settings.instance().pmc().getProperty("leveldb.stats") + "\n\n" + "Memory usage: " + Settings.instance().pmc().getProperty("leveldb.approximate-memory-usage");
        TextController c = new TextController(context, tdlib);
        c.setArguments(TextController.Arguments.fromRawText("App Database Stats", stats, "text/plain"));
        navigateTo(c);
        break;
      }
      case R.id.btn_secret_tdlibDatabaseStats: {
        UI.showToast("Calculating. Please wait...", Toast.LENGTH_SHORT);
        tdlib.client().send(new TdApi.GetDatabaseStatistics(), result -> {
          switch (result.getConstructor()) {
            case TdApi.DatabaseStatistics.CONSTRUCTOR:
              tdlib.ui().post(() -> {
                if (!isDestroyed()) {
                  TextController c = new TextController(context, tdlib);
                  c.setArguments(TextController.Arguments.fromRawText("TDLib Database Stats", ((TdApi.DatabaseStatistics) result).statistics, "text/plain"));
                  navigateTo(c);
                }
              });
              break;
            case TdApi.Error.CONSTRUCTOR:
              UI.showError(result);
              break;
          }
        });
        break;
      }
      case R.id.btn_secret_deleteProfilePhoto: {
        TdApi.User user = tdlib.myUser();
        if (user != null && user.profilePhoto != null) {
          tdlib.client().send(new TdApi.DeleteFile(user.profilePhoto.small.id), tdlib.okHandler());
          tdlib.client().send(new TdApi.DeleteFile(user.profilePhoto.big.id), tdlib.okHandler());
        }
        break;
      }
      case R.id.btn_secret_dropSavedScrollPositions: {
        Settings.instance().removeScrollPositions(tdlib.accountId(), null);
        break;
      }
      case R.id.btn_secret_sendAllChangeLogs: {
        tdlib.checkChangeLogs(false, true);
        break;
      }
      case R.id.btn_secret_readAllChats: {
        showConfirm(Lang.getString(R.string.ReadAllChatsInfo), null, () -> {
          tdlib.readAllChats(null, readCount -> UI.showToast(Lang.plural(R.string.ReadAllChatsDone, readCount), Toast.LENGTH_SHORT));
        });
        break;
      }
      case R.id.btn_tdlib_verbosity: {
        showTdlibVerbositySettings((String) ((ListItem) v.getTag()).getData());
        break;
      }
      case R.id.btn_tdlib_resetLogSettings: {
        Settings.instance().getLogSettings().reset();
        adapter.updateAllValuedSettings();
        UI.showToast("Done. Restart is required for some changes to apply.", Toast.LENGTH_SHORT);
        break;
      }
      case R.id.btn_tdlib_logSize: {
        openInputAlert("Maximum Log Size", "Amount of bytes", R.string.Done, R.string.Cancel, String.valueOf(Settings.instance().getLogSettings().getLogMaxFileSize()), (view, value) -> {
          if (!StringUtils.isNumeric(value)) {
            return false;
          }
          long result = StringUtils.parseLong(value);
          if (result < ByteUnit.KIB.toBytes(1))
            return false;
          Settings.instance().getLogSettings().setMaxFileSize(result);
          adapter.updateValuedSettingById(R.id.btn_tdlib_logSize);
          return true;
        }, true);
        break;
      }
      case R.id.btn_tdlib_viewLogs: {
        viewTdlibLog(v, false);
        break;
      }
      case R.id.btn_tdlib_viewLogsOld: {
        viewTdlibLog(v, true);
        break;
      }
      case R.id.btn_tdlib_androidLogs: {
        Settings.instance().getLogSettings().setNeedAndroidLog(adapter.toggleView(v));
        break;
      }
    }
  }

  @SuppressLint("ResourceType")
  private void showTdlibVerbositySettings (@Nullable String module) {
    List<ListItem> items = new ArrayList<>(7);
    Settings.TdlibLogSettings settings = Settings.instance().getLogSettings();
    int currentVerbosity = settings.getVerbosity(module);
    int defaultVerbosity = module != null ? settings.getDefaultVerbosity(module) : Settings.TDLIB_LOG_VERBOSITY_UNKNOWN;
    int maxVerbosity = 7;
    if (module != null && (defaultVerbosity <= 0 || defaultVerbosity >= maxVerbosity - 1)) {
      int id = defaultVerbosity + 1;
      items.add(new ListItem(ListItem.TYPE_RADIO_OPTION, id, 0, getLogVerbosity(defaultVerbosity) + " (Default)", R.id.btn_tdlib_verbosity, defaultVerbosity == currentVerbosity));
    }
    for (int verbosity = module != null ? 1 : 0; verbosity < 7; verbosity++) {
      boolean isMore = verbosity == 6;
      String name;
      if (isMore) {
        name = "MORE";
      } else {
        name = getLogVerbosity(verbosity);
      }
      if (module != null && verbosity == defaultVerbosity) {
        name = name + " (Default)";
      }
      int id = verbosity + 1;
      items.add(new ListItem(isMore ? ListItem.TYPE_SETTING : ListItem.TYPE_RADIO_OPTION, id, 0, name, R.id.btn_tdlib_verbosity, !isMore && verbosity == currentVerbosity));
    }
    ListItem[] array = new ListItem[items.size()];
    items.toArray(array);
    SettingsWrap[] wrap = new SettingsWrap[1];
    SettingsWrapBuilder b = new SettingsWrapBuilder(R.id.btn_tdlib_verbosity)
      .setRawItems(array)
      .setIntDelegate((id, result) -> {
      int verbosity = result.get(R.id.btn_tdlib_verbosity, 1) - 1;
      settings.setVerbosity(module, verbosity);
      if (StringUtils.isEmpty(module)) {
        adapter.updateAllValuedSettingsById(R.id.btn_tdlib_verbosity);
      } else {
        adapter.updateValuedSettingByData(module);
      }
    })
      .setAllowResize(false);
    b.setOnSettingItemClick((view, settingsId, item, doneButton, settingsAdapter) -> {
      //noinspection ResourceType
      if (item.getId() == 7 && wrap[0] != null && wrap[0].window != null && !wrap[0].window.isWindowHidden()) {
        wrap[0].window.hideWindow(true);
        UI.post(() -> openInputAlert(module != null ? module : "Verbosity Level", "Integer "  + (module != null ? 1 : 0) + ".." + Integer.MAX_VALUE, R.string.Save, R.string.Cancel, Integer.toString(currentVerbosity != Settings.TDLIB_LOG_VERBOSITY_UNKNOWN ? currentVerbosity : 0), (inputView, result) -> {
          if (!StringUtils.isNumeric(result)) {
            return false;
          }
          int verbosity = StringUtils.parseInt(result, Settings.TDLIB_LOG_VERBOSITY_UNKNOWN);
          if (verbosity < 0)
            return false;
          if (module != null && verbosity < 1)
            return false;
          settings.setVerbosity(module, verbosity);
          if (StringUtils.isEmpty(module)) {
            adapter.updateValuedSettingById(R.id.btn_tdlib_verbosity);
          } else {
            adapter.updateValuedSettingByData(module);
          }
          return true;
        }, true), 200);
      }
    });
    wrap[0] = showSettings(b);
  }

  private void viewTdlibLog (View view, final boolean old) {
    checkLogSize(old);
    final int i = old ? 1 : 0;
    if (logSize[i] == 0) {
      UI.showToast("Log is empty", Toast.LENGTH_SHORT);
      return;
    }

    final int size = 4;
    IntList ids = new IntList(size);
    IntList icons = new IntList(size);
    IntList colors = new IntList(size);
    StringList strings = new StringList(size);

    ids.append(R.id.btn_tdlib_viewLogs);
    icons.append(R.drawable.baseline_visibility_24);
    colors.append(OPTION_COLOR_NORMAL);
    strings.append(R.string.Open);

    ids.append(R.id.btn_tdlib_shareLogs);
    icons.append(tdlib == null || tdlib.context().inRecoveryMode() ? R.drawable.baseline_share_24 : R.drawable.baseline_forward_24);
    colors.append(OPTION_COLOR_NORMAL);
    strings.append(R.string.Share);

    ids.append(R.id.btn_saveFile);
    icons.append(R.drawable.baseline_file_download_24);
    colors.append(OPTION_COLOR_NORMAL);
    strings.append(R.string.SaveToDownloads);

    ids.append(R.id.btn_tdlib_clearLogs);
    icons.append(R.drawable.baseline_delete_24);
    colors.append(OPTION_COLOR_RED);
    strings.append(R.string.Delete);

    showOptions(U.getFileName(TdlibManager.getLogFilePath(old)) + " (" + Strings.buildSize(logSize[i]) + ")", ids.get(), strings.get(), colors.get(), icons.get(), (itemView, id) -> {
      switch (id) {
        case R.id.btn_tdlib_viewLogs: {
          TextController textController = new TextController(context, tdlib);
          textController.setArguments(TextController.Arguments.fromFile("TDLib Log", TdlibManager.getLogFilePath(old), "text/plain"));
          navigateTo(textController);
          break;
        }
        case R.id.btn_saveFile: {
          TD.saveToDownloads(new File(TdlibManager.getLogFilePath(old)), "text/plain");
          break;
        }
        case R.id.btn_tdlib_shareLogs: {
          int verbosity = Settings.instance().getTdlibLogSettings().getVerbosity(null);
          if (verbosity == 0) {
            TdlibUi.sendLogs(SettingsBugController.this, old, tdlib == null || tdlib.context().inRecoveryMode());
          } else {
            context().tooltipManager().builder(view).show(this, tdlib, R.drawable.baseline_warning_24, Lang.getMarkdownString(this, R.string.DebugShareError));
          }
          break;
        }
        case R.id.btn_tdlib_clearLogs: {
          TdlibUi.clearLogs(old, arg1 -> setLogSize(arg1, old));
          break;
        }
      }
      return true;
    });
  }

  @Override
  public boolean onLongClick (View v) {
    switch (v.getId()) {
      case R.id.btn_log_files: {
        if (filesLoaded) {
          if (logFiles == null || logFiles.isEmpty()) {
            setLogFiles(Log.getLogFiles());
          }
          if (logFiles != null && !logFiles.isEmpty()) {
            showOptions("Clear " + Strings.buildSize(logFiles.totalSize) + "?", new int[] {R.id.btn_deleteAll, R.id.btn_cancel}, new String[] {"Delete all logs", "Cancel"}, new int[]{OPTION_COLOR_RED, OPTION_COLOR_NORMAL}, new int[]{R.drawable.baseline_delete_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
              if (id == R.id.btn_deleteAll) {
                deleteAllFiles();
              }
              return true;
            });
            return true;
          }
        }
        break;
      }
    }
    return false;
  }

  @Override
  public void onApplySettings (@IdRes int id, SparseIntArray result) {
    switch (id) {
      case R.id.btn_log_verbosity: {
        int level = result.get(R.id.btn_log_verbosity, Log.LEVEL_ERROR) - 1;
        Log.setLogLevel(level);
        adapter.updateValuedSettingById(R.id.btn_log_verbosity);
        break;
      }
      case R.id.btn_log_tags: {
        long tags = 0;
        final int count = result.size();
        for (int i = 0; i < count; i++) {
          tags |= result.keyAt(i);
        }
        Log.setEnabledTags(tags);
        adapter.updateValuedSettingById(R.id.btn_log_tags);
        break;
      }
    }
  }
}