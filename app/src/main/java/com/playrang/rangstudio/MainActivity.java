package com.playrang.rangstudio;
import android.app.Activity; import android.content.*; import android.net.Uri; import android.os.*;
import android.provider.MediaStore; import android.util.Base64; import android.webkit.*; import android.widget.Toast;
import android.Manifest; import android.content.pm.PackageManager; import java.io.OutputStream;
public class MainActivity extends Activity {
  WebView w; ValueCallback<Uri[]> fileCb; PermissionRequest pendingPerm;
  @Override protected void onCreate(Bundle b){ super.onCreate(b);
    w=new WebView(this); setContentView(w);
    WebSettings s=w.getSettings(); s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true);
    s.setMediaPlaybackRequiresUserGesture(false); s.setAllowFileAccess(true); s.setAllowContentAccess(true);
    w.addJavascriptInterface(new Bridge(),"RangAndroid");
    w.setWebViewClient(new WebViewClient(){
      public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r){
        Uri u=r.getUrl(); String sc=u.getScheme();
        if("http".equals(sc)||"https".equals(sc)){ startActivity(new Intent(Intent.ACTION_VIEW,u)); return true;} return false;}
      public void onPageFinished(WebView v,String url){ v.evaluateJavascript(
        "document.addEventListener('click',function(e){var a=e.target.closest&&e.target.closest('a[download]');if(!a)return;var h=a.href;if(h.indexOf('blob:')!==0)return;e.preventDefault();"+
        "var n=a.getAttribute('download')||'rang-studio.webm';fetch(h).then(function(r){return r.blob()}).then(function(bl){var fr=new FileReader();fr.onload=function(){RangAndroid.save(fr.result.split(',')[1],n,bl.type||'video/webm')};fr.readAsDataURL(bl)})},true);",null);}
    });
    w.setWebChromeClient(new WebChromeClient(){
      public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p){
        if(fileCb!=null) fileCb.onReceiveValue(null); fileCb=cb;
        Intent i=p.createIntent(); if(p.getMode()==FileChooserParams.MODE_OPEN_MULTIPLE) i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
        try{ startActivityForResult(i,1);}catch(Exception ex){ fileCb=null; return false;} return true;}
      public void onPermissionRequest(PermissionRequest req){
        if(Build.VERSION.SDK_INT>=23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
          pendingPerm=req; requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},2);} else req.grant(req.getResources());}
    });
    w.loadUrl("file:///android_asset/index.html");
  }
  @Override public void onRequestPermissionsResult(int c,String[] p,int[] g){
    if(c==2&&pendingPerm!=null){ if(g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED) pendingPerm.grant(pendingPerm.getResources()); else pendingPerm.deny(); pendingPerm=null;}}
  @Override protected void onActivityResult(int c,int r,Intent d){
    if(c==1&&fileCb!=null){ Uri[] res=null;
      if(r==RESULT_OK&&d!=null){ if(d.getClipData()!=null){ int n=d.getClipData().getItemCount(); res=new Uri[n]; for(int i=0;i<n;i++) res[i]=d.getClipData().getItemAt(i).getUri(); }
        else if(d.getData()!=null) res=new Uri[]{d.getData()}; }
      fileCb.onReceiveValue(res); fileCb=null; } }
  @Override public void onBackPressed(){ if(w.canGoBack()) w.goBack(); else super.onBackPressed(); }
  class Bridge { @JavascriptInterface public void save(String b64,String name,String mime){
    try{ byte[] data=Base64.decode(b64,Base64.DEFAULT); ContentValues cv=new ContentValues();
      cv.put(MediaStore.MediaColumns.DISPLAY_NAME,name); cv.put(MediaStore.MediaColumns.MIME_TYPE,mime);
      Uri col; if(Build.VERSION.SDK_INT>=29){ cv.put(MediaStore.MediaColumns.RELATIVE_PATH,"Movies/RangStudio"); col=MediaStore.Video.Media.EXTERNAL_CONTENT_URI; }
      else { col=MediaStore.Video.Media.EXTERNAL_CONTENT_URI; }
      Uri u=getContentResolver().insert(col,cv); OutputStream os=getContentResolver().openOutputStream(u); os.write(data); os.close();
      runOnUiThread(()->Toast.makeText(MainActivity.this,"वीडियो Gallery (Movies/RangStudio) में सेव हो गया",Toast.LENGTH_LONG).show());
    }catch(Exception e){ runOnUiThread(()->Toast.makeText(MainActivity.this,"सेव नहीं हुआ: "+e.getMessage(),Toast.LENGTH_LONG).show()); } } }
}
