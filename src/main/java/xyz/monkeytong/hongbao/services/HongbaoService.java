package xyz.monkeytong.hongbao.services;

import android.accessibilityservice.AccessibilityService;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.util.List;

import xyz.monkeytong.hongbao.utils.HongbaoSignature;
import xyz.monkeytong.hongbao.utils.MoneyPackInfo;
import xyz.monkeytong.hongbao.utils.PowerUtil;
import xyz.monkeytong.hongbao.utils.WeChatConstant;


/**
 * 夏敏
 * 红包service
 */
public class HongbaoService extends AccessibilityService implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String WECHAT_DETAILS_EN = "Details";
    private static final String WECHAT_DETAILS_CH = "红包详情";
    private static final String WECHAT_BETTER_LUCK_EN = "Better luck next time!";
    private static final String WECHAT_BETTER_LUCK_CH = "手慢了";
    private static final String WECHAT_EXPIRES_CH = "已超过24小时";
    private static final String WECHAT_VIEW_SELF_CH = "查看红包";
    private static final String WECHAT_VIEW_OTHERS_CH = "领取红包";
    private static final String WECHAT_NOTIFICATION_TIP = "[微信红包]";
    private static final String WECHAT_LUCKMONEY_RECEIVE_ACTIVITY = "LuckyMoneyReceiveUI";
    private static final String WECHAT_LUCKMONEY_DETAIL_ACTIVITY = "LuckyMoneyDetailUI";
    private static final String WECHAT_LUCKMONEY_GENERAL_ACTIVITY = "LauncherUI";
    private static final String WECHAT_LUCKMONEY_CHATTING_ACTIVITY = "ChattingUI";
    private String currentActivityName = WECHAT_LUCKMONEY_GENERAL_ACTIVITY;

    private AccessibilityNodeInfo rootNodeInfo, mReceiveNode, mUnpackNode;
    private boolean mLuckyMoneyPicked, mLuckyMoneyReceived;
    private int mUnpackCount = 0;
    private boolean mMutex = false, mListMutex = false, mChatMutex = false;
    private HongbaoSignature signature = new HongbaoSignature();

    private PowerUtil powerUtil;
    private SharedPreferences sharedPreferences;
    private Handler mHandler;

    @Override
    public void onCreate() {
        super.onCreate();
        mHandler = new Handler();
        WeChatConstant.upDataId(getApplicationContext());
    }

    /**
     * AccessibilityEvent
     *
     * @param event 事件
     */
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (sharedPreferences == null) return;




   //     setCurrentActivityName(event);
    //    watchChatMoney(event);
        watchNotifications(event);
        getPackageMoney(event);
        /* 检测通知消息 */
        if (!mMutex) {
            if (sharedPreferences.getBoolean("pref_watch_notification", false) && watchNotifications(event))
                return;
            if (sharedPreferences.getBoolean("pref_watch_list", false) && watchList(event)) return;
            mListMutex = false;
        }

        if (!mChatMutex) {
            mChatMutex = true;
            if (sharedPreferences.getBoolean("pref_watch_chat", false)) {
                watchChat(event);
                watchChatMoney(event);
            }
            mChatMutex = false;
        }
    }

    private void watchChat(AccessibilityEvent event) {
        this.rootNodeInfo = getRootInActiveWindow();

        if (rootNodeInfo == null) return;

        mReceiveNode = null;
        mUnpackNode = null;

        checkNodeInfo(event.getEventType());

        /* 如果已经接收到红包并且还没有戳开 */
        if (mLuckyMoneyReceived && !mLuckyMoneyPicked && (mReceiveNode != null)) {
            mMutex = true;

            mReceiveNode.getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
            mLuckyMoneyReceived = false;
            mLuckyMoneyPicked = true;
        }
        /* 如果戳开但还未领取 */
        if (mUnpackCount == 1 && (mUnpackNode != null)) {
            int delayFlag = sharedPreferences.getInt("pref_open_delay", 0) * 1000;
            new android.os.Handler().postDelayed(
                    new Runnable() {
                        public void run() {
                            try {
                                mUnpackNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                            } catch (Exception e) {
                                mMutex = false;
                                mLuckyMoneyPicked = false;
                                mUnpackCount = 0;
                            }
                        }
                    },
                    delayFlag);
        }
    }


    /**
     * 获取红包金额
     *
     * @param event
     */
    private void watchChatMoney(AccessibilityEvent event) {
        rootNodeInfo = getRootInActiveWindow();
        if (rootNodeInfo == null) return;
        float mTmpMoney = 0;
        Log.i("iii", " 开始查找金额");
        List<AccessibilityNodeInfo> moneyInfo = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            moneyInfo = rootNodeInfo.findAccessibilityNodeInfosByViewId("com.tencent.mm:id/bgg");
            if (moneyInfo != null && moneyInfo.size() != 0) {
                Log.i("iii", " 找到bb0 金额 查找");

                for (AccessibilityNodeInfo mynode : moneyInfo) {
                    if ("android.widget.TextView".equals(mynode.getClassName())) {

                        //mTmpMoney = Float.parseFloat(mynode.getText().toString());
                        Log.i("iii", " 遍历mynode getText(): " + mTmpMoney);
                    } else {
                        Log.i("iii", " 该node失败" + mynode.getText());
                    }
                }
                Toast.makeText(this, "金额" + mTmpMoney, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void setCurrentActivityName(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            Log.i("iii", " TYPE_WINDOW_STATE_CHANGED");
            return;
        }

        try {
            ComponentName componentName = new ComponentName(
                    event.getPackageName().toString(),
                    event.getClassName().toString()
            );

            getPackageManager().getActivityInfo(componentName, 0);
            currentActivityName = componentName.flattenToShortString();
        } catch (PackageManager.NameNotFoundException e) {
            currentActivityName = WECHAT_LUCKMONEY_GENERAL_ACTIVITY;
        }
    }

    private boolean watchList(AccessibilityEvent event) {
        if (mListMutex) return false;
        mListMutex = true;
        AccessibilityNodeInfo eventSource = event.getSource();
        // Not a message
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED || eventSource == null)
            return false;

        List<AccessibilityNodeInfo> nodes = eventSource.findAccessibilityNodeInfosByText(WECHAT_NOTIFICATION_TIP);
        //增加条件判断currentActivityName.contains(WECHAT_LUCKMONEY_GENERAL_ACTIVITY)
        //避免当订阅号中出现标题为“[微信红包]拜年红包”（其实并非红包）的信息时误判
        if (!nodes.isEmpty() && currentActivityName.contains(WECHAT_LUCKMONEY_GENERAL_ACTIVITY)) {
            AccessibilityNodeInfo nodeToClick = nodes.get(0);
            if (nodeToClick == null) return false;
            CharSequence contentDescription = nodeToClick.getContentDescription();
            if (contentDescription != null && !signature.getContentDescription().equals(contentDescription)) {
                nodeToClick.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                signature.setContentDescription(contentDescription.toString());
                return true;
            }
        }
        return false;
    }


    @Override
    public void onInterrupt() {

    }

    private AccessibilityNodeInfo findOpenButton(AccessibilityNodeInfo node) {
        if (node == null)
            return null;

        //非layout元素
        if (node.getChildCount() == 0) {
            if ("android.widget.Button".equals(node.getClassName()))
                return node;
            else
                return null;
        }

        //layout元素，遍历找button
        AccessibilityNodeInfo button;
        for (int i = 0; i < node.getChildCount(); i++) {
            button = findOpenButton(node.getChild(i));
            if (button != null)
                return button;
        }
        return null;
    }

    private void checkNodeInfo(int eventType) {
        if (this.rootNodeInfo == null) return;

        if (signature.commentString != null) {
            sendComment();
            signature.commentString = null;
        }

        /* 聊天会话窗口，遍历节点匹配“领取红包”和"查看红包" */
        AccessibilityNodeInfo node1 = (sharedPreferences.getBoolean("pref_watch_self", false)) ?
                this.getTheLastNode(WECHAT_VIEW_OTHERS_CH, WECHAT_VIEW_SELF_CH) : this.getTheLastNode(WECHAT_VIEW_OTHERS_CH);
        if (node1 != null &&
                (currentActivityName.contains(WECHAT_LUCKMONEY_CHATTING_ACTIVITY)
                        || currentActivityName.contains(WECHAT_LUCKMONEY_GENERAL_ACTIVITY))) {
            String excludeWords = sharedPreferences.getString("pref_watch_exclude_words", "");
            if (this.signature.generateSignature(node1, excludeWords)) {
                mLuckyMoneyReceived = true;
                mReceiveNode = node1;
                Log.d("sig", this.signature.toString());
            }
            return;
        }

        /* 戳开红包，红包还没抢完，遍历节点匹配“拆红包” */
        AccessibilityNodeInfo node2 = findOpenButton(this.rootNodeInfo);
        if (node2 != null && "android.widget.Button".equals(node2.getClassName()) && currentActivityName.contains(WECHAT_LUCKMONEY_RECEIVE_ACTIVITY)) {
            mUnpackNode = node2;
            mUnpackCount += 1;
            return;
        }

        /* 戳开红包，红包已被抢完，遍历节点匹配“红包详情”和“手慢了” */
        boolean hasNodes = this.hasOneOfThoseNodes(
                WECHAT_BETTER_LUCK_CH, WECHAT_DETAILS_CH,
                WECHAT_BETTER_LUCK_EN, WECHAT_DETAILS_EN, WECHAT_EXPIRES_CH);
        if (mMutex && eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && hasNodes
                && (currentActivityName.contains(WECHAT_LUCKMONEY_DETAIL_ACTIVITY)
                || currentActivityName.contains(WECHAT_LUCKMONEY_RECEIVE_ACTIVITY))) {
            mMutex = false;
            mLuckyMoneyPicked = false;
            mUnpackCount = 0;
            performGlobalAction(GLOBAL_ACTION_BACK);
            signature.commentString = generateCommentString();
        }
    }

    private void sendComment() {
        try {
            AccessibilityNodeInfo outNode =
                    getRootInActiveWindow().getChild(0).getChild(0);
            AccessibilityNodeInfo nodeToInput = outNode.getChild(outNode.getChildCount() - 1).getChild(0).getChild(1);

            if ("android.widget.EditText".equals(nodeToInput.getClassName())) {
                Bundle arguments = new Bundle();
                arguments.putCharSequence(AccessibilityNodeInfo
                        .ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, signature.commentString);
                nodeToInput.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
            }
        } catch (Exception e) {
            // Not supported
        }
    }


    private boolean hasOneOfThoseNodes(String... texts) {
        List<AccessibilityNodeInfo> nodes;
        for (String text : texts) {
            if (text == null) continue;

            nodes = this.rootNodeInfo.findAccessibilityNodeInfosByText(text);

            if (nodes != null && !nodes.isEmpty()) return true;
        }
        return false;
    }

    private AccessibilityNodeInfo getTheLastNode(String... texts) {
        int bottom = 0;
        AccessibilityNodeInfo lastNode = null, tempNode;
        List<AccessibilityNodeInfo> nodes;

        for (String text : texts) {
            if (text == null) continue;

            nodes = this.rootNodeInfo.findAccessibilityNodeInfosByText(text);
            if (nodes != null && !nodes.isEmpty()) {
                tempNode = nodes.get(nodes.size() - 1);
                if (tempNode == null) return null;
                Rect bounds = new Rect();
                tempNode.getBoundsInScreen(bounds);
                if (bounds.bottom > bottom) {
                    bottom = bounds.bottom;
                    lastNode = tempNode;
                    signature.others = text.equals(WECHAT_VIEW_OTHERS_CH);
                }
            }
        }
        return lastNode;
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        this.watchFlagsFromPreference();
    }

    private void watchFlagsFromPreference() {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);

        this.powerUtil = new PowerUtil(this);
        Boolean watchOnLockFlag = sharedPreferences.getBoolean("pref_watch_on_lock", false);
        this.powerUtil.handleWakeLock(watchOnLockFlag);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals("pref_watch_on_lock")) {
            Boolean changedValue = sharedPreferences.getBoolean(key, false);
            this.powerUtil.handleWakeLock(changedValue);
        }
    }

    @Override
    public void onDestroy() {
        this.powerUtil.handleWakeLock(false);
        super.onDestroy();
    }

    private String generateCommentString() {
        if (!signature.others) return null;

        Boolean needComment = sharedPreferences.getBoolean("pref_comment_switch", false);
        if (!needComment) return null;

        String[] wordsArray = sharedPreferences.getString("pref_comment_words", "").split(" +");
        if (wordsArray.length == 0) return null;

        Boolean atSender = sharedPreferences.getBoolean("pref_comment_at", false);
        if (atSender) {
            return "@" + signature.sender + " " + wordsArray[(int) (Math.random() * wordsArray.length)];
        } else {
            return wordsArray[(int) (Math.random() * wordsArray.length)];
        }
    }
    public final static String TAG = "MoneyCounter";

    private boolean watchNotifications(AccessibilityEvent event) {
        // 判断是不是一个通知
        if (event.getEventType() != AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED)
            return false;

        // 判断是不是红包
        String tip = event.getText().toString();
        if (!tip.contains(WeChatConstant.WECHAT_NOTIFICATION_TIP)) {
            Log.d(TAG, "不是微信红包");
            return true;
        }
        Log.d(TAG, "来了一个微信红包");
        Parcelable parcelable = event.getParcelableData();
        if (parcelable instanceof Notification) {
            Notification notification = (Notification) parcelable;
            try {
                /* 清除signature,避免进入会话后误判 */
                // signature.cleanSignature();
                Log.d(TAG, "点击进入");
                notification.contentIntent.send();
            } catch (PendingIntent.CanceledException e) {
                e.printStackTrace();
            }
        }
        return true;
    }
    Boolean mOpenPack = false;

    private void getPackageMoney(AccessibilityEvent event) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            rootNodeInfo = getRootInActiveWindow();
        }
        if (rootNodeInfo == null) return;
        /**
         * 寻找红包名
         */
        List<AccessibilityNodeInfo> itemInfo = null;
        Log.d(TAG, "寻找红包名");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {

            itemInfo = rootNodeInfo.findAccessibilityNodeInfosByViewId(WeChatConstant.RED_PACK_LAYOUT);
            if (itemInfo != null && itemInfo.size() != 0) {
                AccessibilityNodeInfo layoutnode = itemInfo.get(itemInfo.size() - 1);
                Log.d(TAG, "找到红包layout " + itemInfo.size() + "个");
                if ("android.widget.LinearLayout".equals(layoutnode.getClassName())) {
                    Log.d(TAG, "找到红包item");
                    if (layoutnode.findAccessibilityNodeInfosByText(WeChatConstant.WECHAT_PACK_TIP) != null
                            && layoutnode.findAccessibilityNodeInfosByText(WeChatConstant.WECHAT_VIEW_OTHERS_CH) != null) {
                        Log.d(TAG, "找到微信红包 ");
                        if (layoutnode.getParent() != null) {
                            List<AccessibilityNodeInfo> nameInfo = null;
                            String name = "";
                            nameInfo = layoutnode.getParent().findAccessibilityNodeInfosByViewId(WeChatConstant.RED_PACK_NAME);
                            if (nameInfo != null && nameInfo.size() != 0) {
                                name = nameInfo.get(0).getText().toString();
                            }
                            List<AccessibilityNodeInfo> textInfo = null;
                            String text = "";
                            textInfo = layoutnode.getParent().findAccessibilityNodeInfosByViewId(WeChatConstant.RED_PACK_TEXT);
                            if (textInfo != null && textInfo.size() != 0) {
                                text = textInfo.get(0).getText().toString();
                            }
                            Log.i(TAG, "该红包 name " + name + " text " + text);

                            if (MoneyPackInfo.checkPack(name, text)) {
                                if (text != null && !TextUtils.isEmpty(text) && text.length() != 0) {
                                    char c = text.charAt(text.length() - 1);
                                    Log.i(TAG, "该红包未在点击过列表中，点击红包  结尾字符" + c);
                                        layoutnode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                        mOpenPack = true;
                                }
                            }
                        }
                    }
                }
            }

            /**
             * 寻找点击按钮
             */

            itemInfo = rootNodeInfo.findAccessibilityNodeInfosByViewId(WeChatConstant.WECHAT_Click_button);
            if (itemInfo != null && itemInfo.size() != 0) {
                AccessibilityNodeInfo layoutnode = itemInfo.get(0);
                if (layoutnode != null) {
                    Log.i(TAG, "点击打开红包");
                    layoutnode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    mOpenPack = true;
                }
            }

            itemInfo = rootNodeInfo.findAccessibilityNodeInfosByViewId(WeChatConstant.WECHAT_BETTER_LUCK_CH);
            if (itemInfo != null && itemInfo.size() != 0) {
                itemInfo = rootNodeInfo.findAccessibilityNodeInfosByViewId(WeChatConstant.WECHAT_cancel_button);
                if (itemInfo != null && itemInfo.size() != 0) {
                    AccessibilityNodeInfo layoutnode = itemInfo.get(0);
                    if (layoutnode != null) {

                        if (mOpenPack) {
                            Log.i(TAG, "点击关闭红包");
                            layoutnode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                            mOpenPack = false;
                        }

                    }
                }
            }


            itemInfo = rootNodeInfo.findAccessibilityNodeInfosByViewId(WeChatConstant.WECHAT_BACK);
            if (itemInfo != null && itemInfo.size() != 0) {
                final AccessibilityNodeInfo layoutnode = itemInfo.get(0);
                if (layoutnode != null) {
                    Log.i(TAG, "找到详情页 返回按钮 mOpenPack = " + mOpenPack);
                    if (mOpenPack) {
                        Log.i(TAG, "点击关闭详情");
                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                layoutnode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                mOpenPack = false;
                            }
                        }, 100);
                    }
                }
            }

        }
    }

}
