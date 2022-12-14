package com.hyphenate.helpdesk.easeui.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.text.ClipboardManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.hyphenate.chat.ChatClient;
import com.hyphenate.chat.ChatManager;
import com.hyphenate.chat.Conversation;
import com.hyphenate.chat.Message;
import com.hyphenate.helpdesk.R;
import com.hyphenate.helpdesk.easeui.UIProvider;
import com.hyphenate.helpdesk.easeui.provider.CustomChatRowProvider;
import com.hyphenate.helpdesk.easeui.recorder.MediaManager;
import com.hyphenate.helpdesk.easeui.runtimepermission.PermissionsManager;
import com.hyphenate.helpdesk.easeui.runtimepermission.PermissionsResultAction;
import com.hyphenate.helpdesk.easeui.util.CommonUtils;
import com.hyphenate.helpdesk.easeui.util.Config;
import com.hyphenate.helpdesk.easeui.widget.AlertDialog;
import com.hyphenate.helpdesk.easeui.widget.AlertDialog.AlertDialogUser;
import com.hyphenate.helpdesk.easeui.widget.EaseChatInputMenu;
import com.hyphenate.helpdesk.easeui.widget.EaseChatInputMenu.ChatInputMenuListener;
import com.hyphenate.helpdesk.easeui.widget.ExtendMenu.EaseChatExtendMenuItemClickListener;
import com.hyphenate.helpdesk.easeui.widget.MessageList;
import com.hyphenate.helpdesk.easeui.widget.MessageList.MessageListItemClickListener;
import com.hyphenate.helpdesk.easeui.widget.ToastHelper;
import com.hyphenate.helpdesk.emojicon.Emojicon;
import com.hyphenate.helpdesk.manager.EmojiconManager;
import com.hyphenate.helpdesk.model.AgentIdentityInfo;
import com.hyphenate.helpdesk.model.QueueIdentityInfo;
import com.hyphenate.helpdesk.model.VisitorInfo;
import com.hyphenate.util.EMLog;
import com.hyphenate.util.PathUtil;
import com.hyphenate.util.UriUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.FragmentTransaction;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener;

/**
 * ????????????new?????????????????????????????????fragment???
 * ??????????????????setArguments????????????IM?????????
 * app???????????????fragment??????
 * ???????????????????????????demo??????ChatActivity
 */
public class ChatFragment extends BaseFragment implements ChatManager.MessageListener, EmojiconManager.EmojiconManagerDelegate {

    protected static final String TAG = ChatFragment.class.getSimpleName();
    private static final String STATE_SAVE_IS_HIDDEN = "STATE_SAVE_IS_HIDDEN";
    protected static final int REQUEST_CODE_CAMERA = 1;
    protected static final int REQUEST_CODE_LOCAL = 2;
    private static final int REQUEST_CODE_SELECT_VIDEO = 3;

    public static final int REQUEST_CODE_EVAL = 5;
    public static final int REQUEST_CODE_SELECT_FILE = 6;
    /**
     * ??????fragment?????????
     */
    protected Bundle fragmentArgs;
    protected String toChatUsername;
    protected boolean showUserNick;
    protected MessageList messageList;
    protected EaseChatInputMenu inputMenu;

    protected Conversation conversation;
    protected InputMethodManager inputManager;
    protected ClipboardManager clipboard;
    protected String cameraFilePath = null;
    protected SwipeRefreshLayout swipeRefreshLayout;
    protected ListView listView;

    protected boolean isloading;
    protected boolean haveMoreData = true;
    protected int pagesize = 20;
    protected Message contextMenuMessage;

    protected static final int ITEM_TAKE_PICTURE = 1;
    protected static final int ITEM_PICTURE = 2;
    protected static final int ITEM_VIDEO = 3;
    protected static final int ITEM_FILE = 4;

    protected int[] itemStrings = {R.string.attach_take_pic, R.string.attach_picture, R.string.attach_video, R.string.attach_file};
    protected int[] itemdrawables = {R.drawable.hd_chat_takepic_selector, R.drawable.hd_chat_image_selector, R.drawable.hd_chat_video_selector, R.drawable.hd_chat_file_selector};

    protected int[] itemIds = {ITEM_TAKE_PICTURE, ITEM_PICTURE, ITEM_VIDEO, ITEM_FILE};
    protected int[] itemResIds = {R.id.chat_menu_take_pic, R.id.chat_menu_pic, R.id.chat_menu_video, R.id.chat_menu_file};
    private boolean isMessageListInited;
    protected MyMenuItemClickListener extendMenuItemClickListener;
    private VisitorInfo visitorInfo;
    private AgentIdentityInfo agentIdentityInfo;
    private QueueIdentityInfo queueIdentityInfo;
    private String titleName;
    protected TextView tvTipWaitCount;
    private Dialog mNoticeDialog;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            boolean isSupportedHidden = savedInstanceState.getBoolean(STATE_SAVE_IS_HIDDEN);
            FragmentTransaction ft = getFragmentManager().beginTransaction();
            if (isSupportedHidden) {
                ft.hide(this);
            } else {
                ft.show(this);
            }
            ft.commit();
        }
        ChatClient.getInstance().emojiconManager().addDelegate(this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.hd_fragment_chat, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {

        fragmentArgs = getArguments();
        // IM?????????
        toChatUsername = fragmentArgs.getString(Config.EXTRA_SERVICE_IM_NUMBER);
        // ????????????????????????
        showUserNick = fragmentArgs.getBoolean(Config.EXTRA_SHOW_NICK, false);
        //???????????????
        queueIdentityInfo = fragmentArgs.getParcelable(Config.EXTRA_QUEUE_INFO);
        //????????????
        agentIdentityInfo = fragmentArgs.getParcelable(Config.EXTRA_AGENT_INFO);
        visitorInfo = fragmentArgs.getParcelable(Config.EXTRA_VISITOR_INFO);

        titleName = fragmentArgs.getString(Config.EXTRA_TITLE_NAME);
        //?????????????????????initView???setUpView????????????
        super.onActivityCreated(savedInstanceState);
        if (savedInstanceState != null) {
            cameraFilePath = savedInstanceState.getString("cameraFilePath");
        }
        ChatClient.getInstance().chatManager().bindChat(toChatUsername);
        //?????? ?????????????????????????????????
        initPermission();
        ChatClient.getInstance().chatManager().addAgentInputListener(agentInputListener);

        // ??????????????????????????????????????????
        setUserNameView();
    }

    //==================????????????===============================

    /**
     * ????????????????????????
     */
    private void showPermissionNoticeDialog() {
        View layout = LayoutInflater.from(requireContext()).inflate(R.layout.hd_dialog_permission_notice, null);
        mNoticeDialog = new android.app.AlertDialog.Builder(requireContext(), R.style.Theme_Notice_Dialog).create();
        mNoticeDialog.setCancelable(true);
        mNoticeDialog.setCanceledOnTouchOutside(true);
        mNoticeDialog.show();
        Window window = mNoticeDialog.getWindow();
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
        window.setGravity(Gravity.TOP);
        window.setContentView(layout);
    }

    private static final int PERMISSION_CODE = 11;


    /**
     * ????????????
     */
    private void initPermission() {
        String[] permissionsArray = new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO};
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP) {
            boolean storageDenied = ActivityCompat.checkSelfPermission(requireContext(), permissionsArray[0]) != PackageManager.PERMISSION_GRANTED;
            boolean recordDenied = ActivityCompat.checkSelfPermission(requireContext(), permissionsArray[1]) != PackageManager.PERMISSION_GRANTED;
            if (storageDenied || recordDenied) {
                requestPermissions(permissionsArray, PERMISSION_CODE);
                showPermissionNoticeDialog();
            }
        }
        //SDK?????????????????????????????????????????????
//        PermissionsManager.getInstance().requestPermissionsIfNecessaryForResult(this, permissionsArray,
//                new PermissionsResultAction() {
//                    @Override
//                    public void onGranted() {
//                        if (mNoticeDialog != null) {
//                            mNoticeDialog.dismiss();
//                        }
//                    }
//
//                    @Override
//                    public void onDenied(String permission) {
//                        if (mNoticeDialog != null) {
//                            mNoticeDialog.dismiss();
//                        }
//                    }
//                });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_CODE) {
            //???????????????????????????????????????
            if (mNoticeDialog != null && mNoticeDialog.isShowing()) {
                mNoticeDialog.dismiss();
            }
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("huanxin", "????????????");
            } else if (!ActivityCompat.shouldShowRequestPermissionRationale(requireActivity(), permissions[0])) {
                Log.d("huanxin", "??????????????????????????????????????????????????????????????????????????????");
                Toast.makeText(requireContext(),"????????????????????????????????????????????????",Toast.LENGTH_LONG).show();
            } else {
                Log.d("huanxin", "???????????????,??????????????????");
            }
            Log.i("HuanXinChat", "?????????????????????" + permissions + ",???????????????" + grantResults);
        }
    }

    //=====================================================

    private void setUserNameView() {
        if (ChatClient.getInstance().isLoggedInBefore()) {
            String currentUsername = ChatClient.getInstance().currentUserName();
            if (getView() != null) {
                TextView tvUname = (TextView) getView().findViewById(R.id.tv_username);
                if (tvUname != null) {
                    tvUname.setText(currentUsername);
                }
            }
        }
    }

    /**
     * init view
     */
    @Override
    protected void initView() {
        // ????????????layout
        messageList = (MessageList) getView().findViewById(R.id.message_list);
        messageList.setShowUserNick(showUserNick);
        listView = messageList.getListView();
        tvTipWaitCount = (TextView) getView().findViewById(R.id.tv_tip_waitcount);
        extendMenuItemClickListener = new MyMenuItemClickListener();
        inputMenu = (EaseChatInputMenu) getView().findViewById(R.id.input_menu);
        registerExtendMenuItem();
        // init input menu
        inputMenu.init();
        inputMenu.setChatInputMenuListener(new ChatInputMenuListener() {

            @Override
            public void onSendMessage(String content) {
                // ??????????????????
                sendTextMessage(content);
            }

            @Override
            public void onBigExpressionClicked(Emojicon emojicon) {
                if (!TextUtils.isEmpty(emojicon.getBigIconRemotePath())) {
                    sendCustomEmojiMessage(emojicon.getBigIconRemotePath());
                } else if (!TextUtils.isEmpty(emojicon.getIconRemotePath())) {
                    sendCustomEmojiMessage(emojicon.getIconRemotePath());
                } else if (!TextUtils.isEmpty(emojicon.getBigIconPath())) {
                    sendImageMessage(emojicon.getBigIconPath());
                } else if (!TextUtils.isEmpty(emojicon.getIconPath())) {
                    sendImageMessage(emojicon.getIconPath());
                }
            }

            @Override
            public void onRecorderCompleted(float seconds, String filePath) {
                // ??????????????????
                int time = seconds > 1 ? (int) seconds : 1;
                sendVoiceMessage(filePath, time);
            }
        });
        inputMenu.setHasSendButton(true);

        swipeRefreshLayout = messageList.getSwipeRefreshLayout();
        swipeRefreshLayout.setColorSchemeResources(R.color.holo_blue_bright, R.color.holo_green_light,
                R.color.holo_orange_light, R.color.holo_red_light);

        inputManager = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        clipboard = (ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
        getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        ChatClient.getInstance().chatManager().addVisitorWaitListener(visitorWaitListener);
    }

    ChatManager.VisitorWaitListener visitorWaitListener = new ChatManager.VisitorWaitListener() {
        @Override
        public void waitCount(final int num) {
            if (getActivity() == null) {
                return;
            }
//            EMLog.d(TAG, "waitCount--num:" + num);
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (num > 0) {
                        tvTipWaitCount.setVisibility(View.VISIBLE);
                        tvTipWaitCount.setText(getString(R.string.current_wait_count, num));
                    } else {
                        tvTipWaitCount.setVisibility(View.GONE);
                    }
                }
            });
        }
    };

    ChatManager.AgentInputListener agentInputListener = new ChatManager.AgentInputListener() {
        @Override
        public void onInputState(final String input) {
            if (getActivity() == null) {
                return;
            }
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (input != null) {
                        titleBar.setTitle(input);
                    } else {
                        if (!TextUtils.isEmpty(titleName)) {
                            titleBar.setTitle(titleName);
                        } else {
                            titleBar.setTitle(toChatUsername);
                        }
                    }
                }
            });

        }
    };

    /**
     * ????????????????????????
     */
    @Override
    protected void setUpView() {
        if (!TextUtils.isEmpty(titleName)) {
            titleBar.setTitle(titleName);
        } else {
            titleBar.setTitle(toChatUsername);
        }

        titleBar.setRightImageResource(R.drawable.hd_mm_title_remove);

        onConversationInit();
        onMessageListInit();

        // ???????????????????????????
        titleBar.setLeftLayoutClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                if (getActivity() != null) {
                    getActivity().finish();
                }
            }
        });
        titleBar.setRightLayoutClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                emptyHistory();
            }
        });
        setRefreshLayoutListener();

        // test api
//        ChatClient.getInstance().chatManager().getTransferGuideMenu(toChatUsername, new ValueCallBack<JSONObject>() {
//            @Override
//            public void onSuccess(JSONObject value) {
//                EMLog.d(TAG, "onsuccess" + value.toString());
//                Message message = Message.createReceiveMessage(Message.Type.TXT);
//                message.setBody(new EMTextMessageBody("test guide"));
//                message.setMsgId(UUID.randomUUID().toString());
//                message.setStatus(Message.Status.SUCCESS);
//                message.setFrom(toChatUsername);
//                message.setMessageTime(System.currentTimeMillis());
//                try {
//                    message.setAttribute(Message.KEY_MSGTYPE, value.getJSONObject(Message.KEY_MSGTYPE));
//                    ChatClient.getInstance().chatManager().saveMessage(message);
//                    messageList.refreshSelectLast();
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//            }
//
//
//            @Override
//            public void onError(int error, String errorMsg) {
//
//            }
//        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        ChatClient.getInstance().emojiconManager().removeDelegate(this);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        ChatClient.getInstance().chatManager().unbindChat();
        ChatClient.getInstance().chatManager().removeAgentInputListener(agentInputListener);
        ChatClient.getInstance().chatManager().removeVisitorWaitListener(visitorWaitListener);
    }

    /**
     * ???????????????????????????item; ???????????????????????????????????????item???item???id?????????3
     */
    protected void registerExtendMenuItem() {
        for (int i = 0; i < itemStrings.length; i++) {
            inputMenu.registerExtendMenuItem(itemStrings[i], itemdrawables[i], itemIds[i], itemResIds[i], extendMenuItemClickListener);
        }
    }

    protected void onConversationInit() {
        // ????????????conversation??????
        conversation = ChatClient.getInstance().chatManager().getConversation(toChatUsername);
        if (conversation != null) {
            // ??????????????????????????????0
            conversation.markAllMessagesAsRead();
            final List<Message> msgs = conversation.getAllMessages();
            int msgCount = msgs != null ? msgs.size() : 0;
            if (msgCount < conversation.getAllMsgCount() && msgCount < pagesize) {
                String msgId = null;
                if (msgs != null && msgs.size() > 0) {
                    msgId = msgs.get(0).messageId();
                }
                conversation.loadMessages(msgId, pagesize - msgCount);
            }
        }

    }

    protected void onMessageListInit() {
        messageList.init(toChatUsername, chatFragmentListener != null ?
                chatFragmentListener.onSetCustomChatRowProvider() : null);
        //??????list item???????????????????????????
        setListItemClickListener();

        messageList.getListView().setOnTouchListener(new OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (!inputMenu.isVoiceRecording()) {//????????????????????????????????????
                    hideKeyboard();
                    inputMenu.hideExtendMenuContainer();
                }
                return false;
            }
        });

        isMessageListInited = true;
    }

    protected void setListItemClickListener() {
        messageList.setItemClickListener(new MessageListItemClickListener() {

            @Override
            public void onUserAvatarClick(String username) {
                if (chatFragmentListener != null) {
                    chatFragmentListener.onAvatarClick(username);
                }
            }

            @Override
            public void onResendClick(final Message message) {
                new AlertDialog(getActivity(), R.string.resend, R.string.confirm_resend, null, new AlertDialogUser() {
                    @Override
                    public void onResult(boolean confirmed, Bundle bundle) {
                        if (!confirmed) {
                            return;
                        }
                        ChatClient.getInstance().chatManager().resendMessage(message);
                    }
                }, true).show();
            }

            @Override
            public void onBubbleLongClick(Message message) {
                contextMenuMessage = message;
                if (chatFragmentListener != null) {
                    chatFragmentListener.onMessageBubbleLongClick(message);
                }
            }

            @Override
            public boolean onBubbleClick(Message message) {
                if (chatFragmentListener != null) {
                    return chatFragmentListener.onMessageBubbleClick(message);
                }
                return false;
            }

            @Override
            public void onMessageItemClick(Message message, MessageList.ItemAction action) {
                contextMenuMessage = message;
                if (chatFragmentListener != null) {
                    chatFragmentListener.onMessageItemClick(message, action);
                }
            }
        });
    }

    protected void setRefreshLayoutListener() {
        swipeRefreshLayout.setOnRefreshListener(new OnRefreshListener() {

            @Override
            public void onRefresh() {
                new Handler().postDelayed(new Runnable() {

                    @Override
                    public void run() {
                        if (getActivity() == null || getActivity().isFinishing()) {
                            return;
                        }
                        if (listView.getFirstVisiblePosition() == 0 && !isloading && haveMoreData) {
                            List<Message> messages = null;
                            try {
                                messages = conversation.loadMessages(messageList.getItem(0).messageId(),
                                        pagesize);
                            } catch (Exception e1) {
                                swipeRefreshLayout.setRefreshing(false);
                                return;
                            }
                            if (messages != null && messages.size() > 0) {
                                messageList.refreshSeekTo(messages.size() - 1);
                                if (messages.size() != pagesize) {
                                    haveMoreData = false;
                                }
                            } else {
                                haveMoreData = false;
                            }

                            isloading = false;

                        } else {
                            ToastHelper.show(getActivity(), R.string.no_more_messages);
                        }
                        swipeRefreshLayout.setRefreshing(false);
                    }
                }, 600);
            }
        });
    }

    @SuppressLint("NewApi")
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == REQUEST_CODE_CAMERA) { // ????????????
                sendImageMessage(cameraFilePath);
            } else if (requestCode == REQUEST_CODE_LOCAL) { // ??????????????????
                if (data != null) {
                    Uri selectedImage = data.getData();
                    if (selectedImage != null) {
                        sendPicByUri(selectedImage);
                    }
                }
            } else if (requestCode == REQUEST_CODE_SELECT_FILE) {
                if (data != null) {
                    Uri uri = data.getData();
                    if (uri != null) {
                        sendFileByUri(uri);
                    }
                }
            } else if (requestCode == REQUEST_CODE_SELECT_VIDEO) {
                if (data != null) {
                    int duration = data.getIntExtra("dur", 0);
                    String videoPath = data.getStringExtra("path");
                    String uriString = data.getStringExtra("uri");
                    EMLog.d(TAG, "path = " + videoPath + " uriString = " + uriString);
                    if (!TextUtils.isEmpty(videoPath)) {
                        File file = new File(PathUtil.getInstance().getVideoPath(), "thvideo" + System.currentTimeMillis());
                        try {
                            FileOutputStream fos = new FileOutputStream(file);
                            Bitmap ThumbBitmap = ThumbnailUtils.createVideoThumbnail(videoPath, 3);
                            ThumbBitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                            fos.close();
                            sendVideoMessage(videoPath, file.getAbsolutePath(), duration);
                        } catch (Exception e) {
                            e.printStackTrace();
                            EMLog.e(TAG, e.getMessage());
                        }
                    } else {
                        Uri videoUri = UriUtils.getLocalUriFromString(uriString);
                        File file = new File(PathUtil.getInstance().getVideoPath(), "thvideo" + System.currentTimeMillis());
                        try {
                            FileOutputStream fos = new FileOutputStream(file);
                            MediaMetadataRetriever media = new MediaMetadataRetriever();
                            media.setDataSource(getContext(), videoUri);
                            Bitmap frameAtTime = media.getFrameAtTime();
                            frameAtTime.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                            fos.close();
                            sendVideoMessage(videoUri, file.getAbsolutePath(), duration);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isMessageListInited)
            messageList.refresh();
        MediaManager.resume();
        UIProvider.getInstance().pushActivity(getActivity());
        // register the event listener when enter the foreground
        ChatClient.getInstance().chatManager().addMessageListener(this);
    }


    @Override
    public void onStop() {
        super.onStop();
        // unregister this event listener when this activity enters the
        // background
        ChatClient.getInstance().chatManager().removeMessageListener(this);
        // ??????activity ???foreground activity ???????????????
        UIProvider.getInstance().popActivity(getActivity());
    }


    public void onBackPressed() {
        inputMenu.onBackPressed();
    }

    @Override
    public void onEmojiconChanged() {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ToastHelper.show(getActivity(), R.string.emoji_icon_update);
                if (inputMenu != null) {
                    inputMenu.onEmojiconChanged();
                }
            }
        });
    }

    /**
     * ???????????????item????????????
     */
    class MyMenuItemClickListener implements EaseChatExtendMenuItemClickListener {

        @Override
        public void onExtendMenuItemClick(int itemId, View view) {
            if (getActivity() == null) {
                return;
            }
            if (chatFragmentListener != null) {
                if (chatFragmentListener.onExtendMenuItemClick(itemId, view)) {
                    return;
                }
            }
            switch (itemId) {
                case ITEM_TAKE_PICTURE: // ??????
                    PermissionsManager.getInstance().requestPermissionsIfNecessaryForResult(ChatFragment.this, new String[]{Manifest.permission.CAMERA}, new PermissionsResultAction() {
                        @Override
                        public void onGranted() {
                            selectPicFromCamera();
                        }

                        @Override
                        public void onDenied(String permission) {

                        }
                    });
                    break;
                case ITEM_PICTURE:
                    selectPicFromLocal(); // ??????????????????
                    break;
                case ITEM_VIDEO:
                    PermissionsManager.getInstance().requestPermissionsIfNecessaryForResult(ChatFragment.this, new String[]{Manifest.permission.CAMERA}, new PermissionsResultAction() {
                        @Override
                        public void onGranted() {
                            selectVideoFromLocal();
                        }

                        @Override
                        public void onDenied(String permission) {

                        }
                    });

                    break;
                case ITEM_FILE:
                    //????????????
                    //demo?????????????????????api?????????????????????app??????????????????qq????????????????????????
                    selectFileFromLocal();
                    break;
                default:
                    break;
            }
        }

    }

    private void selectVideoFromLocal() {
        Intent intent = new Intent(getActivity(), ImageGridActivity.class);
        startActivityForResult(intent, REQUEST_CODE_SELECT_VIDEO);
    }

    /**
     * ????????????
     */
    protected void selectFileFromLocal() {
        Intent intent = null;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) { //19????????????api????????????demo???????????????????????????????????????
            intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);

        } else {
            intent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        }
        startActivityForResult(intent, REQUEST_CODE_SELECT_FILE);
    }


    /**
     * ??????????????????uri????????????
     *
     * @param selectedImage
     */
    protected void sendPicByUri(Uri selectedImage) {
        sendImageMessage(selectedImage);
    }

    /**
     * ??????uri????????????
     *
     * @param uri
     */
    protected void sendFileByUri(Uri uri) {
        sendFileMessage(uri);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_SAVE_IS_HIDDEN, isHidden());
        if (cameraFilePath != null) {
            outState.putString("cameraFile", cameraFilePath);
        }
    }

    /**
     * ??????????????????
     */
    protected void selectPicFromCamera() {
        if (!CommonUtils.isExitsSdcard()) {
            ToastHelper.show(getActivity(), R.string.sd_card_does_not_exist);
            return;
        }
        try {
            File cameraFile = new File(PathUtil.getInstance().getImagePath(), ChatClient.getInstance().currentUserName()
                    + System.currentTimeMillis() + ".jpg");
            cameraFilePath = cameraFile.getAbsolutePath();
            if (!cameraFile.getParentFile().exists()) {
                cameraFile.getParentFile().mkdirs();
            }
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) {
                intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(cameraFile));
            } else {
                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, FileProvider.getUriForFile(getContext().getApplicationContext(),
                        getContext().getPackageName() + ".fileprovider", cameraFile));
            }
            startActivityForResult(intent, REQUEST_CODE_CAMERA);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * ?????????????????????
     */
    protected void selectPicFromLocal() {
        Intent intent;
        if (Build.VERSION.SDK_INT < 19) {
            intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");

        } else {
            intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        }
        startActivityForResult(intent, REQUEST_CODE_LOCAL);
    }


    /**
     * ????????????????????????
     */
    protected void emptyHistory() {
        String msg = getResources().getString(R.string.Whether_to_empty_all_chats);
        new AlertDialog(getActivity(), null, msg, null, new AlertDialogUser() {

            @Override
            public void onResult(boolean confirmed, Bundle bundle) {
                if (confirmed) {
                    MediaManager.release();
                    ChatClient.getInstance().chatManager().clearConversation(toChatUsername);
                    messageList.refresh();
                }
            }
        }, true).show();
    }


    /**
     * ???????????????
     */
    protected void hideKeyboard() {
        if (getActivity().getWindow().getAttributes().softInputMode != WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN) {
            if (getActivity().getCurrentFocus() != null)
                inputManager.hideSoftInputFromWindow(getActivity().getCurrentFocus().getWindowToken(),
                        InputMethodManager.HIDE_NOT_ALWAYS);
        }
    }

    protected EaseChatFragmentListener chatFragmentListener;

    public void setChatFragmentListener(EaseChatFragmentListener chatFragmentListener) {
        this.chatFragmentListener = chatFragmentListener;
    }

    public interface EaseChatFragmentListener {
        /**
         * ????????????????????????
         *
         * @param username
         */
        void onAvatarClick(String username);

        /**
         * ???????????????????????????
         */
        boolean onMessageBubbleClick(Message message);

        /**
         * ???????????????????????????
         */
        void onMessageBubbleLongClick(Message message);

        /**
         * ???????????????item????????????,???????????????EaseChatFragment????????????????????????return true
         *
         * @param view
         * @param itemId
         * @return
         */
        boolean onExtendMenuItemClick(int itemId, View view);

        /**
         * ?????????????????????????????????????????????????????????????????????
         */
        void onMessageItemClick(Message message, MessageList.ItemAction action);

        /**
         * ???????????????chatrow?????????
         *
         * @return
         */
        CustomChatRowProvider onSetCustomChatRowProvider();
    }

    @Override
    public void onMessage(List<Message> msgs) {
        for (Message message : msgs) {
            String username = null;
            username = message.from();

            // ???????????????????????????????????????????????????
            if (username != null && username.equals(toChatUsername)) {
                messageList.refreshSelectLast();
                // ?????????????????????????????????
                UIProvider.getInstance().getNotifier().viberateAndPlayTone(message);
            } else {
                // ?????????????????????????????????ID?????????
                UIProvider.getInstance().getNotifier().onNewMsg(message);
            }
        }

    }

    @Override
    public void onCmdMessage(List<Message> msgs) {

    }


    @Override
    public void onMessageStatusUpdate() {
        messageList.refreshSelectLast();
    }

    @Override
    public void onMessageSent() {
        messageList.refreshSelectLast();
    }

    // ??????????????????
    //=============================================
    protected void sendTextMessage(String content) {
        if (content != null && content.length() > 1500) {
            ToastHelper.show(getActivity(), R.string.message_content_beyond_limit);
            return;
        }
        Message message = Message.createTxtSendMessage(content, toChatUsername);
        attachMessageAttrs(message);
        ChatClient.getInstance().chatManager().sendMessage(message);
        messageList.refreshSelectLast();
    }

    protected void sendVoiceMessage(String filePath, int length) {
        if (TextUtils.isEmpty(filePath)) {
            return;
        }
        Message message = Message.createVoiceSendMessage(filePath, length, toChatUsername);
        attachMessageAttrs(message);
        ChatClient.getInstance().chatManager().sendMessage(message);
        messageList.refreshSelectLast();
    }

    protected void sendImageMessage(String imagePath) {
        if (TextUtils.isEmpty(imagePath)) {
            return;
        }
        File imageFile = new File(imagePath);
        if (!imageFile.exists()) {
            return;
        }

        Message message = Message.createImageSendMessage(imagePath, false, toChatUsername);
        attachMessageAttrs(message);
        ChatClient.getInstance().chatManager().sendMessage(message);
        messageList.refreshSelectLastDelay(MessageList.defaultDelay);
    }

    protected void sendImageMessage(Uri imageUri) {
        Message message = Message.createImageSendMessage(imageUri, false, toChatUsername);
        if (message != null) {
            attachMessageAttrs(message);
            ChatClient.getInstance().chatManager().sendMessage(message);
            messageList.refreshSelectLastDelay(MessageList.defaultDelay);
        }
    }

    protected void sendCustomEmojiMessage(String imagePath) {
        if (TextUtils.isEmpty(imagePath)) {
            return;
        }

        Message message = Message.createCustomEmojiSendMessage(imagePath, toChatUsername);
        message.setMessageTime(System.currentTimeMillis());
        attachMessageAttrs(message);
        ChatClient.getInstance().chatManager().sendMessage(message);
        messageList.refreshSelectLastDelay(MessageList.defaultDelay);
    }

    protected void sendFileMessage(String filePath) {
        Message message = Message.createFileSendMessage(filePath, toChatUsername);
        attachMessageAttrs(message);
        ChatClient.getInstance().chatManager().sendMessage(message);
        messageList.refreshSelectLastDelay(MessageList.defaultDelay);
    }

    protected void sendFileMessage(Uri fileUri) {
        Message message = Message.createFileSendMessage(fileUri, toChatUsername);
        attachMessageAttrs(message);
        ChatClient.getInstance().chatManager().sendMessage(message);
        messageList.refreshSelectLastDelay(MessageList.defaultDelay);
    }

    protected void sendLocationMessage(double latitude, double longitude, String locationAddress, String toChatUsername) {
        Message message = Message.createLocationSendMessage(latitude, longitude, locationAddress, toChatUsername);
        attachMessageAttrs(message);
        ChatClient.getInstance().chatManager().sendMessage(message);
        messageList.refreshSelectLastDelay(MessageList.defaultDelay);
    }

    protected void sendVideoMessage(String videoPath, String thumbPath, int videoLength) {
        Message message = Message.createVideoSendMessage(videoPath, thumbPath, videoLength, toChatUsername);
        attachMessageAttrs(message);
        ChatClient.getInstance().chatManager().sendMessage(message);
        messageList.refreshSelectLastDelay(MessageList.defaultDelay);
    }

    protected void sendVideoMessage(Uri videoUri, String thumbPath, int videoLength) {
        Message message = Message.createVideoSendMessage(videoUri, thumbPath, videoLength, toChatUsername);
        attachMessageAttrs(message);
        ChatClient.getInstance().chatManager().sendMessage(message);
        messageList.refreshSelectLastDelay(MessageList.defaultDelay);
    }

    public void attachMessageAttrs(Message message) {
        if (visitorInfo != null) {
            message.addContent(visitorInfo);
        }
        if (queueIdentityInfo != null) {
            message.addContent(queueIdentityInfo);
        }
        if (agentIdentityInfo != null) {
            message.addContent(agentIdentityInfo);
        }

    }

    @Override
    public void onPause() {
        super.onPause();
        MediaManager.pause();
    }


}
