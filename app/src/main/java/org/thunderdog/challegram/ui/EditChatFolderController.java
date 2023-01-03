package org.thunderdog.challegram.ui;

import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.CheckResult;
import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.component.dialogs.ChatView;
import org.thunderdog.challegram.component.user.RemoveHelper;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.AvatarPlaceholder;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGFoundChat;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.BetterChatView;
import org.thunderdog.challegram.widget.MaterialEditTextGroup;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import me.vkryl.core.ArrayUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.LongList;

public class EditChatFolderController extends RecyclerViewController<EditChatFolderController.Arguments> implements View.OnClickListener, SettingsAdapter.TextChangeListener, SelectChatsController.Delegate {

  private static final int NO_CHAT_FILTER_ID = 0;
  private static final TdApi.ChatFilter EMPTY_CHAT_FILTER = TD.newChatFilter();

  public static class Arguments {
    private final int chatFilterId;
    private final String chatFilterName;
    private final @Nullable TdApi.ChatFilter chatFilter;

    public static Arguments newFolder () {
      return new Arguments(NO_CHAT_FILTER_ID, (TdApi.ChatFilter) null);
    }

    public static Arguments newFolder (@Nullable TdApi.ChatFilter chatFilter) {
      return new Arguments(NO_CHAT_FILTER_ID, chatFilter);
    }

    public Arguments (TdApi.ChatFilterInfo chatFilterInfo) {
      this(chatFilterInfo.id, chatFilterInfo.title);
    }

    public Arguments (int chatFilterId, @Nullable TdApi.ChatFilter chatFilter) {
      this(chatFilterId, chatFilter != null ? chatFilter.title : "", chatFilter);
    }

    public Arguments (int chatFilterId, String chatFilterName) {
      this(chatFilterId, chatFilterName, null);
    }

    private Arguments (int chatFilterId, String chatFilterName, @Nullable TdApi.ChatFilter chatFilter) {
      this.chatFilter = chatFilter;
      this.chatFilterId = chatFilterId;
      this.chatFilterName = chatFilterName;
    }
  }

  public static EditChatFolderController newFolder (Context context, Tdlib tdlib) {
    EditChatFolderController controller = new EditChatFolderController(context, tdlib);
    controller.setArguments(Arguments.newFolder());
    return controller;
  }

  public static EditChatFolderController newFolder (Context context, Tdlib tdlib, TdApi.ChatFilter chatFilter) {
    EditChatFolderController controller = new EditChatFolderController(context, tdlib);
    controller.setArguments(Arguments.newFolder(chatFilter));
    return controller;
  }

  @SuppressWarnings("FieldCanBeLocal")
  private final @IdRes int includedChatsPreviousItemId = R.id.btn_folderIncludeChats;
  @SuppressWarnings("FieldCanBeLocal")
  private final @IdRes int excludedChatsPreviousItemId = R.id.btn_folderExcludeChats;
  private final @IdRes int includedChatsNextItemId = ViewCompat.generateViewId();
  private final @IdRes int excludedChatsNextItemId = ViewCompat.generateViewId();

  private SettingsAdapter adapter;
  private ListItem input;

  private int chatFilterId;
  private TdApi.ChatFilter originChatFilter;
  private TdApi.ChatFilter editedChatFilter;

  public EditChatFolderController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public boolean needAsynchronousAnimation () {
    return originChatFilter == null && chatFilterId != NO_CHAT_FILTER_ID;
  }

  @Override
  public long getAsynchronousAnimationTimeout (boolean fastAnimation) {
    return 500l;
  }

  @Override
  public int getId () {
    return R.id.controller_editChatFolders;
  }

  @Override
  public CharSequence getName () {
    Arguments arguments = getArgumentsStrict();
    return chatFilterId != NO_CHAT_FILTER_ID ? arguments.chatFilterName : Lang.getString(R.string.NewFolder);
  }

  @Override
  public void setArguments (Arguments args) {
    super.setArguments(args);
    this.chatFilterId = args.chatFilterId;
    this.originChatFilter = args.chatFilter;
    this.editedChatFilter = args.chatFilter != null ? TD.copyOf(args.chatFilter) : TD.newChatFilter(args.chatFilterName);
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    ArrayList<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_HEADER_PADDED, 0, 0, R.string.FolderName));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(input = new ListItem(ListItem.TYPE_EDITTEXT_POLL_OPTION, R.id.input, 0, 0).setStringValue(editedChatFilter.title));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    items.add(new ListItem(ListItem.TYPE_HEADER_PADDED, 0, 0, R.string.FolderIncludedChats));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_folderIncludeChats, R.drawable.baseline_add_circle_24, R.string.FolderActionIncludeChats).setTextColorId(R.id.theme_color_inlineText));
    if (editedChatFilter != null) {
      fillIncludedChats(editedChatFilter, items);
    }
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM, includedChatsNextItemId));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.FolderIncludedChatsInfo));

    items.add(new ListItem(ListItem.TYPE_HEADER_PADDED, 0, 0, R.string.FolderExcludedChats));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_folderExcludeChats, R.drawable.baseline_add_circle_24, R.string.FolderActionExcludeChats).setTextColorId(R.id.theme_color_inlineText));
    if (editedChatFilter != null) {
      fillExcludedChats(editedChatFilter, items);
    }
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM, excludedChatsNextItemId));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.FolderExcludedChatsInfo));

    if (chatFilterId != NO_CHAT_FILTER_ID) {
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_removeFolder, R.drawable.baseline_folder_delete_24, R.string.RemoveFolder).setTextColorId(R.id.theme_color_textNegative));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      items.add(new ListItem(ListItem.TYPE_PADDING).setHeight(Screen.dp(12f)));
    }

    adapter = new Adapter(this);
    adapter.setLockFocusOn(this, /* showAlways */ StringUtils.isEmpty(editedChatFilter.title));
    adapter.setTextChangeListener(this);
    adapter.setItems(items, false);
    recyclerView.setAdapter(adapter);
    RemoveHelper.attach(recyclerView, new RemoveHelperCallback());

    if (originChatFilter == null && chatFilterId != NO_CHAT_FILTER_ID) {
      loadChatFilter();
    }
  }

  @Override
  public boolean saveInstanceState (Bundle outState, String keyPrefix) {
    super.saveInstanceState(outState, keyPrefix);
    Arguments arguments = getArgumentsStrict();
    outState.putInt(keyPrefix + "_chatFilterId", arguments.chatFilterId);
    outState.putString(keyPrefix + "_chatFilterName", arguments.chatFilterName);
    TD.saveChatFilter(outState, keyPrefix + "_originChatFilter", originChatFilter);
    TD.saveChatFilter(outState, keyPrefix + "_editedChatFilter", editedChatFilter);
    return true;
  }

  @Override
  public boolean restoreInstanceState (Bundle in, String keyPrefix) {
    super.restoreInstanceState(in, keyPrefix);
    int chatFilterId = in.getInt(keyPrefix + "_chatFilterId", NO_CHAT_FILTER_ID);
    String chatFilterName = in.getString(keyPrefix + "_chatFilterName");
    TdApi.ChatFilter originChatFilter = TD.restoreChatFilter(in, keyPrefix + "_originChatFilter");
    TdApi.ChatFilter editedChatFilter = TD.restoreChatFilter(in, keyPrefix + "_editedChatFilter");
    if (chatFilterName != null && editedChatFilter != null) {
      super.setArguments(new Arguments(chatFilterId, chatFilterName, originChatFilter));
      this.chatFilterId = chatFilterId;
      this.originChatFilter = originChatFilter;
      this.editedChatFilter = editedChatFilter;
      return true;
    }
    return false;
  }

  @Override
  protected int getMenuId () {
    return R.id.menu_done;
  }

  @Override
  public void fillMenuItems (int id, HeaderView header, LinearLayout menu) {
    if (id == R.id.menu_done) {
      header.addDoneButton(menu, this).setVisibility(canSaveChanges() ? View.VISIBLE : View.GONE);
    }
  }

  @Override
  public void onMenuItemPressed (int id, View view) {
    if (id == R.id.menu_btn_done) {
      saveChanges();
    }
  }

  @Override
  public void onClick (View v) {
    int id = v.getId();
    if (id == R.id.btn_folderIncludeChats) {
      SelectChatsController selectChats = new SelectChatsController(context, tdlib);
      selectChats.setArguments(SelectChatsController.Arguments.includedChats(this, editedChatFilter));
      navigateTo(selectChats);
    } else if (id == R.id.btn_folderExcludeChats) {
      SelectChatsController selectChats = new SelectChatsController(context, tdlib);
      selectChats.setArguments(SelectChatsController.Arguments.excludedChats(this, editedChatFilter));
      navigateTo(selectChats);
    } else if (id == R.id.btn_removeFolder) {
      showRemoveFolderConfirm();
    } else if (id == R.id.chat || ArrayUtils.contains(TD.CHAT_TYPES, id)) {
      int position = getRecyclerView().getChildAdapterPosition(v);
      ListItem item = (ListItem) v.getTag();
      showRemoveConditionConfirm(position, item);
    }
  }

  @Override
  public boolean onBackPressed (boolean fromTop) {
    if (hasChanges()) {
      showUnsavedChangesPromptBeforeLeaving(/* onConfirm */ null);
      return true;
    }
    return super.onBackPressed(fromTop);
  }

  @Override
  public void onBlur () {
    super.onBlur();
    adapter.setLockFocusOn(this, false);
    setLockFocusView(getLockFocusView(), false);
  }

  @Override
  public void onPrepareToShow () {
    super.onPrepareToShow();
    updateMenuButton();
  }

  @Override
  public void onTextChanged (int id, ListItem item, MaterialEditTextGroup v, String text) {
    editedChatFilter.title = text;
    updateMenuButton();
  }

  private int indexOfFirstIncludedChat () {
    int index = adapter.indexOfViewById(includedChatsPreviousItemId);
    return index == RecyclerView.NO_POSITION ? RecyclerView.NO_POSITION : index + 1;
  }

  private int indexOfFirstExcludedChat () {
    int index = adapter.indexOfViewById(excludedChatsPreviousItemId);
    return index == RecyclerView.NO_POSITION ? RecyclerView.NO_POSITION : index + 1;
  }

  private void fillIncludedChats (TdApi.ChatFilter chatFilter, List<ListItem> outList) {
    for (int includedChatType : TD.includedChatTypes(chatFilter)) {
      outList.add(new ListItem(ListItem.TYPE_SEPARATOR));
      outList.add(chatTypeItem(includedChatType));
    }
    for (long pinnedChatId : chatFilter.pinnedChatIds) {
      outList.add(new ListItem(ListItem.TYPE_SEPARATOR));
      outList.add(chatItem(pinnedChatId).setBoolValue(true));
    }
    for (long includedChatId : chatFilter.includedChatIds) {
      outList.add(new ListItem(ListItem.TYPE_SEPARATOR));
      outList.add(chatItem(includedChatId).setBoolValue(true));
    }
  }

  private void fillExcludedChats (TdApi.ChatFilter chatFilter, List<ListItem> outList) {
    for (int excludedChatType : TD.excludedChatTypes(chatFilter)) {
      outList.add(new ListItem(ListItem.TYPE_SEPARATOR));
      outList.add(chatTypeItem(excludedChatType));
    }
    if (chatFilter.excludedChatIds != null) {
      for (long excludedChatId : chatFilter.excludedChatIds) {
        outList.add(new ListItem(ListItem.TYPE_SEPARATOR));
        outList.add(chatItem(excludedChatId).setBoolValue(false));
      }
    }
  }

  private ListItem chatItem (long chatId) {
    TGFoundChat foundChat = new TGFoundChat(tdlib, null, chatId, true);
    foundChat.setNoUnread();
    return new ListItem(ListItem.TYPE_CHAT_BETTER, R.id.chat).setData(foundChat).setLongId(chatId);
  }

  private ListItem chatTypeItem (@IdRes int id) {
    return new ListItem(ListItem.TYPE_CHAT_BETTER, id, TD.chatTypeIcon24(id), TD.chatTypeName(id)).setIntValue(TD.chatTypeColor(id));
  }

  private void loadChatFilter () {
    tdlib.send(new TdApi.GetChatFilter(chatFilterId), (result) -> runOnUiThreadOptional(() -> {
      switch (result.getConstructor()) {
        case TdApi.ChatFilter.CONSTRUCTOR:
          updateChatFilter((TdApi.ChatFilter) result);
          break;
        case TdApi.Error.CONSTRUCTOR:
          UI.showError(result);
          break;
      }
    }));
  }

  private void updateChatFilter (TdApi.ChatFilter chatFilter) {
    this.editedChatFilter = chatFilter;
    updateMenuButton();
    ArrayList<ListItem> itemList = new ArrayList<>(0);

    int indexOfFirstIncludedChat = indexOfFirstIncludedChat();
    int includedChatsItemCount = adapter.indexOfViewById(includedChatsNextItemId) - indexOfFirstIncludedChat;
    if (includedChatsItemCount > 0) {
      adapter.removeRange(indexOfFirstIncludedChat, includedChatsItemCount);
    }
    if (indexOfFirstIncludedChat != RecyclerView.NO_POSITION) {
      ArrayUtils.ensureCapacity(itemList, chatFilter.includedChatIds.length + 5);
      fillIncludedChats(chatFilter, itemList);
      adapter.addItems(indexOfFirstIncludedChat, itemList.toArray(new ListItem[0]));
    }
    int indexOfFirstExcludedChat = indexOfFirstExcludedChat();
    int excludedChatsItemCount = adapter.indexOfViewById(excludedChatsNextItemId) - indexOfFirstExcludedChat;
    if (excludedChatsItemCount > 0) {
      adapter.removeRange(indexOfFirstExcludedChat, excludedChatsItemCount);
    }
    if (indexOfFirstExcludedChat != RecyclerView.NO_POSITION) {
      itemList.clear();
      ArrayUtils.ensureCapacity(itemList, chatFilter.excludedChatIds.length + 3);
      fillExcludedChats(chatFilter, itemList);
      adapter.addItems(indexOfFirstExcludedChat, itemList.toArray(new ListItem[0]));
    }
  }

  private void updateFolderName () {
    if (StringUtils.isEmpty(editedChatFilter.title) && editedChatFilter.pinnedChatIds.length == 0 && editedChatFilter.includedChatIds.length == 0) {
      int[] includedChatTypes = TD.includedChatTypes(editedChatFilter);
      if (includedChatTypes.length == 1) {
        String chatTypeName = Lang.getString(TD.chatTypeName(includedChatTypes[0]));
        if (input.setStringValueIfChanged(chatTypeName)) {
          editedChatFilter.title = chatTypeName;
          adapter.updateSimpleItemById(input.getId());
        }
      }
    }
  }

  @Override
  public void onSelectedChatsChanged (int mode, Set<Long> chatIds, Set<Integer> chatTypes) {
    if (mode == SelectChatsController.MODE_FOLDER_INCLUDE_CHATS) {
      if (chatIds.isEmpty()) {
        editedChatFilter.pinnedChatIds = ArrayUtils.EMPTY_LONGS;
        editedChatFilter.includedChatIds = ArrayUtils.EMPTY_LONGS;
      } else {
        LongList pinnedChatIds = new LongList(chatIds.size());
        LongList includedChatIds = new LongList(chatIds.size());
        for (long chatId : chatIds) {
          if (ArrayUtils.contains(editedChatFilter.pinnedChatIds, chatId) || (originChatFilter != null && ArrayUtils.contains(originChatFilter.pinnedChatIds, chatId))) {
            pinnedChatIds.append(chatId);
          } else {
            includedChatIds.append(chatId);
          }
        }
        editedChatFilter.pinnedChatIds = pinnedChatIds.get();
        editedChatFilter.includedChatIds = includedChatIds.get();
      }
      editedChatFilter.excludedChatIds = removeAll(editedChatFilter.excludedChatIds, chatIds);
      TD.updateIncludedChatTypes(editedChatFilter, chatTypes::contains);
      updateChatFilter(editedChatFilter);
      updateFolderName();
    } else if (mode == SelectChatsController.MODE_FOLDER_EXCLUDE_CHATS) {
      editedChatFilter.pinnedChatIds = removeAll(editedChatFilter.pinnedChatIds, chatIds);
      editedChatFilter.includedChatIds = removeAll(editedChatFilter.includedChatIds, chatIds);
      editedChatFilter.excludedChatIds = toArray(chatIds);
      TD.updateExcludedChatTypes(editedChatFilter, chatTypes::contains);
      updateChatFilter(editedChatFilter);
      updateFolderName();
    } else {
      throw new UnsupportedOperationException();
    }
  }

  private void showRemoveConditionConfirm (int position, ListItem item) {
    boolean inclusion = item.getBoolValue();
    CharSequence title;
    if (item.getId() == R.id.chat) {
      title = ((TGFoundChat) item.getData()).getFullTitle();
    } else {
      title = item.getString();
    }
    CharSequence info = Lang.getStringBold(inclusion ? R.string.FolderRemoveInclusionConfirm : R.string.FolderRemoveExclusionConfirm, title);
    showConfirm(info, Lang.getString(R.string.Remove), R.drawable.baseline_delete_24, OPTION_COLOR_RED, () -> {
      int index = adapter.getItem(position) == item ? position : adapter.indexOfView(item);
      if (index != RecyclerView.NO_POSITION) {
        adapter.removeRange(index - 1, 2); /* separator, condition */
      }
      if (item.getId() == R.id.chat) {
        long chatId = item.getLongId();
        if (inclusion) {
          editedChatFilter.pinnedChatIds = ArrayUtils.removeElement(editedChatFilter.pinnedChatIds, ArrayUtils.indexOf(editedChatFilter.pinnedChatIds, chatId));
          editedChatFilter.includedChatIds = ArrayUtils.removeElement(editedChatFilter.includedChatIds, ArrayUtils.indexOf(editedChatFilter.includedChatIds, chatId));
        } else {
          editedChatFilter.excludedChatIds = ArrayUtils.removeElement(editedChatFilter.excludedChatIds, ArrayUtils.indexOf(editedChatFilter.excludedChatIds, chatId));
        }
      } else if (item.getId() == R.id.chatType_contact) {
        editedChatFilter.includeContacts = false;
      } else if (item.getId() == R.id.chatType_nonContact) {
        editedChatFilter.includeNonContacts = false;
      } else if (item.getId() == R.id.chatType_group) {
        editedChatFilter.includeGroups = false;
      } else if (item.getId() == R.id.chatType_channel) {
        editedChatFilter.includeChannels = false;
      } else if (item.getId() == R.id.chatType_bot) {
        editedChatFilter.includeBots = false;
      } else if (item.getId() == R.id.chatType_muted) {
        editedChatFilter.excludeMuted = false;
      } else if (item.getId() == R.id.chatType_read) {
        editedChatFilter.excludeRead = false;
      } else if (item.getId() == R.id.chatType_archived) {
        editedChatFilter.excludeArchived = false;
      }
      updateFolderName();
      updateMenuButton();
    });
  }

  private void showRemoveFolderConfirm () {
    showConfirm(Lang.getString(R.string.RemoveFolderConfirm), Lang.getString(R.string.Remove), R.drawable.baseline_delete_24, OPTION_COLOR_RED, () -> {
      deleteChatFilter(chatFilterId);
    });
  }

  private boolean hasChanges () {
    TdApi.ChatFilter originChatFilter = this.originChatFilter != null ? this.originChatFilter : EMPTY_CHAT_FILTER;
    TdApi.ChatFilter editedChatFilter = this.editedChatFilter != null ? this.editedChatFilter : EMPTY_CHAT_FILTER;
    return !TD.contentEquals(originChatFilter, editedChatFilter);
  }

  private boolean canSaveChanges () {
    if (StringUtils.isEmpty(editedChatFilter.title)) {
      return false;
    }
    return (editedChatFilter.includeContacts || editedChatFilter.includeNonContacts || editedChatFilter.includeGroups || editedChatFilter.includeChannels || editedChatFilter.includeBots || editedChatFilter.pinnedChatIds.length > 0 || editedChatFilter.includedChatIds.length > 0) &&
      (chatFilterId == NO_CHAT_FILTER_ID || hasChanges());
  }

  private void saveChanges () {
    if (chatFilterId != NO_CHAT_FILTER_ID) {
      editChatFilter(chatFilterId, TD.copyOf(editedChatFilter));
    } else {
      createChatFilter(TD.copyOf(editedChatFilter));
    }
  }

  private void createChatFilter (TdApi.ChatFilter chatFilter) {
    tdlib.send(new TdApi.CreateChatFilter(chatFilter), tdlib.okHandler(TdApi.ChatFilterInfo.class, this::closeSelf));
  }

  private void editChatFilter (int chatFilterId, TdApi.ChatFilter chatFilter) {
    tdlib.send(new TdApi.EditChatFilter(chatFilterId, chatFilter), tdlib.okHandler(TdApi.ChatFilterInfo.class, this::closeSelf));
  }

  private void deleteChatFilter (int chatFilterId) {
    tdlib.send(new TdApi.DeleteChatFilter(chatFilterId), tdlib.okHandler(this::closeSelf));
  }

  private void closeSelf () {
    if (!isDestroyed()) {
      navigateBack();
    }
  }

  private void updateMenuButton () {
    if (headerView != null) {
      headerView.updateButton(getMenuId(), R.id.menu_btn_done, canSaveChanges() ? View.VISIBLE : View.GONE, 0);
    }
  }

  private class Adapter extends SettingsAdapter {
    public Adapter (ViewController<?> context) {
      super(context);
    }
    @Override
    protected void setChatData (ListItem item, int position, BetterChatView chatView) {
      if (item.getId() == R.id.chat) {
        chatView.setNoSubtitle(false);
        chatView.setChat((TGFoundChat) item.getData());
        chatView.setAllowMaximizePreview(false);
      } else {
        float avatarRadius = ChatView.getAvatarSizeDp(Settings.CHAT_MODE_2LINE) / 2f;
        AvatarPlaceholder avatarPlaceholder = new AvatarPlaceholder(avatarRadius, new AvatarPlaceholder.Metadata(item.getIntValue(), item.getIconResource()), chatView);
        chatView.setTitle(item.getString());
        chatView.setSubtitle(null);
        chatView.setNoSubtitle(true);
        chatView.setAvatar((ImageFile) null, avatarPlaceholder);
        chatView.clearPreviewChat();
      }
    }

    @Override
    protected void modifyEditText (ListItem item, ViewGroup parent, MaterialEditTextGroup editText) {
      editText.setEmptyHint(R.string.FolderNameHint);
      editText.setText(item.getStringValue());
      editText.setMaxLength(12, false);
      editText.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
      ViewSupport.setThemedBackground(parent, R.id.theme_color_filling, EditChatFolderController.this);
    }

    @Override
    protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
      if (item.getId() == R.id.btn_folderIncludeChats || item.getId() == R.id.btn_folderExcludeChats) {
        view.setIconColorId(R.id.theme_color_inlineIcon);
      } else if (item.getId() == R.id.btn_removeFolder) {
        view.setIconColorId(R.id.theme_color_iconNegative);
      } else {
        view.setIconColorId(0 /* theme_color_icon */);
      }
    }
  }

  private class RemoveHelperCallback implements RemoveHelper.Callback {
    @Override
    public boolean canRemove (RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, int position) {
      return viewHolder.getItemViewType() == ListItem.TYPE_CHAT_BETTER;
    }

    @Override
    public void onRemove (RecyclerView.ViewHolder viewHolder) {
      ListItem item = (ListItem) viewHolder.itemView.getTag();
      int position = viewHolder.getAbsoluteAdapterPosition();
      showRemoveConditionConfirm(position, item);
    }
  }

  @CheckResult
  private static long[] removeAll (long[] items, Set<Long> itemsToRemove) {
    if (itemsToRemove.isEmpty() || items.length == 0) {
      return items;
    }
    LongList itemList = new LongList(items.length);
    for (long item : items) {
      if (!itemsToRemove.contains(item)) {
        itemList.append(item);
      }
    }
    return itemList.get();
  }

  @CheckResult
  private static long[] toArray(Collection<Long> collection) {
    if (collection.isEmpty()) {
      return ArrayUtils.EMPTY_LONGS;
    }
    int index = 0;
    long[] array = new long[collection.size()];
    for (long element : collection) {
      array[index++] = element;
    }
    return array;
  }
}
