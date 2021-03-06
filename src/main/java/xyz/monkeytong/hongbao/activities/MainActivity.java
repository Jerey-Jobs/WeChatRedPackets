package xyz.monkeytong.hongbao.activities;

import android.Manifest;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.cn.jerey.permissiontools.Callback.PermissionCallbacks;
import com.cn.jerey.permissiontools.PermissionTools;

import java.util.List;

import xyz.monkeytong.hongbao.R;


@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
public class MainActivity extends Activity implements AccessibilityManager.AccessibilityStateChangeListener {

    //开关切换按钮
    private TextView pluginStatusText;
    private ImageView pluginStatusIcon;
    //AccessibilityService 管理
    private AccessibilityManager accessibilityManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
       // CrashReport.initCrashReport(getApplicationContext(), "900019352", false);
        setContentView(R.layout.activity_main);
        pluginStatusText = (TextView) findViewById(R.id.layout_control_accessibility_text);
        pluginStatusIcon = (ImageView) findViewById(R.id.layout_control_accessibility_icon);

        handleMaterialStatusBar();

        explicitlyLoadPreferences();

        //监听AccessibilityService 变化
        accessibilityManager = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            accessibilityManager.addAccessibilityStateChangeListener(this);
        }
        updateServiceStatus();
        new PermissionTools.Builder(MainActivity.this)
                .setRequestCode(100)
                .setOnPermissionCallbacks(new PermissionCallbacks() {
                    @Override
                    public void onPermissionsGranted(int requestCode, List<String> perms) {

                    }

                    @Override
                    public void onPermissionsDenied(int requestCode, List<String> perms) {

                    }
                })
                .build()
                .requestPermissions(Manifest.permission.CAMERA);


    }

    private void explicitlyLoadPreferences() {
        PreferenceManager.setDefaultValues(this, R.xml.general_preferences, false);
    }

    /**
     * 适配MIUI沉浸状态栏
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void handleMaterialStatusBar() {
        // Not supported in APK level lower than 21
        if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return;

        Window window = this.getWindow();

        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);

        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);

        window.setStatusBarColor(0xffE46C62);

    }

    @Override
    protected void onResume() {
        super.onResume();

    }

    @Override
    protected void onDestroy() {
        //移除监听服务
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            accessibilityManager.removeAccessibilityStateChangeListener(this);
        }
        super.onDestroy();
    }

    public void openAccessibility(View view) {
        try {
            Toast.makeText(this, "点击「闪电侠红包助手」" + pluginStatusText.getText(), Toast.LENGTH_SHORT).show();
            Intent accessibleIntent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(accessibleIntent);
        } catch (Exception e) {
            Toast.makeText(this, "遇到一些问题,请手动打开系统设置>无障碍服务>闪电侠红包助手抢红包插件", Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }


    public void openSettings(View view) {
        Intent settingsIntent = new Intent(this, SettingsActivity.class);
        settingsIntent.putExtra("title", "偏好设置");
        settingsIntent.putExtra("frag_id", "GeneralSettingsFragment");
        startActivity(settingsIntent);
    }


    @Override
    public void onAccessibilityStateChanged(boolean enabled) {
        updateServiceStatus();
    }

    /**
     * 更新当前 HongbaoService 显示状态
     */
    private void updateServiceStatus() {
        if (isServiceEnabled()) {
            pluginStatusText.setText(R.string.service_off);
            pluginStatusIcon.setBackgroundResource(R.mipmap.ic_stop);
        } else {
            pluginStatusText.setText(R.string.service_on);
            pluginStatusIcon.setBackgroundResource(R.mipmap.ic_start);
        }
    }

    /**
     * 获取 HongbaoService 是否启用状态
     *
     * @return
     */
    private boolean isServiceEnabled() {
        List<AccessibilityServiceInfo> accessibilityServices =
                null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            accessibilityServices = accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC);
        }
        for (AccessibilityServiceInfo info : accessibilityServices) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                if (info.getId().equals(getPackageName() + "/.services.HongbaoService")) {
                    return true;
                }
            }
        }
        return false;
    }
}
