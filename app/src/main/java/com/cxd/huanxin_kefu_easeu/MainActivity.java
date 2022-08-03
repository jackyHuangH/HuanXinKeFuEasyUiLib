package com.cxd.huanxin_kefu_easeu;


import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

import com.hyphenate.chat.ChatClient;
import com.hyphenate.helpdesk.callback.Callback;
import com.hyphenate.helpdesk.easeui.UIProvider;
import com.hyphenate.helpdesk.easeui.util.IntentBuilder;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /*环信SDK */
        ChatClient.Options options = new ChatClient.Options();
        options.setAppkey("1464201221092511#kefuchannelapp88412");//必填项，appkey获取地址：kefu.easemob.com，“管理员模式 > 渠道管理 > 手机APP”页面的关联的“AppKey”
        options.setTenantId("88412");//必填项，tenantId获取地址：kefu.easemob.com，“管理员模式 > 设置 > 企业信息”页面的“租户ID”

        // Kefu SDK 初始化
        if (ChatClient.getInstance().init(this, options)){
            // Kefu EaseUI的初始化
            UIProvider.getInstance().init(this);
            //后面可以设置其他属性

            //开启日志
            ChatClient.getInstance().init(this, new ChatClient.Options().setConsoleLog(true));
            //通过反射更改com.hyphenate.helpdesk.easeui.ui.BaseChatActivity的contentView布局从左到右

        }


        final String username = "aaa";
        final String password = "aaa";

        //登录
        ChatClient.getInstance().login(username, password, new Callback() {
            @Override
            public void onSuccess() {
                //进入会话
                Intent intent = new IntentBuilder(MainActivity.this)
                        //获取地址：kefu.easemob.com，“管理员模式 > 渠道管理 > 手机APP”页面的关联的“IM服务号”
                        .setServiceIMNumber("kefuchannelimid_280322")
                        .build();
                startActivity(intent);
            }

            @Override
            public void onError(int code, String error) {
            }

            @Override
            public void onProgress(int progress, String status) {

            }
        });
    }
}