#!/usr/bin/env python3
"""Host doubles exercise production helpers; no Android/LSPosed runtime implied."""
import argparse
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
DOUBLES = {
'android/os/Build.java': 'package android.os; public class Build { public static class VERSION { public static int SDK_INT=35; } }',
'com/samsung/android/app/facewidget/presentation/NowBarMediaCardView.java': '''package com.samsung.android.app.facewidget.presentation;
import android.view.View;
public class NowBarMediaCardView extends View {
 public boolean l0; public int j0; public float w0=100;
 public boolean C, D; public boolean M=true; public float E=0f;
 public View p1=new View(),o1=new View(),t1=new View(),v1=new View(),w1=new View(),P=new View();
 public NowBarMediaCardView(){
  registerChild("stack_background_cardview",p1);
  registerChild("nowbar_cardview_background",o1);
  registerChild("nowbar_cardview_bgview_for_media_setting",t1);
  registerChild("background_shader_view",v1);
  registerChild("background_shader_parent",w1);
  registerChild("nowbar_card_border_stroke_view",P);
 }
 public boolean isExpanded(){return l0;} public int getCardState(){return j0;}
 public View getBorderStrokeView(){return P;}
}''',
'com/samsung/android/app/facewidget/presentation/NowBarOngoingCardView.java': '''package com.samsung.android.app.facewidget.presentation;
import android.view.View;
public class NowBarOngoingCardView extends android.view.ViewGroup {
 public static class NowbarCardSettingView extends View {}
 public boolean D=true, u=false; public float w=0f; public int U=0; public int w0=-1;
 public Object s1=new Object();
 public View B=new View(), F=new View(), x=null, y=null, l0=new View();
 public android.view.ViewGroup G=new android.view.ViewGroup();
  public View K=new NowbarCardSettingView();
  public View nudge=new View(), normalText=new View(), chronRoot=new View();
  public com.google.android.material.card.MaterialCardView actionBg=new com.google.android.material.card.MaterialCardView();
  public int bCalls=0, eCalls=0;
  public NowBarOngoingCardView(){
   registerChild("nowbar_cardview_background",B);
   registerChild("normal_content_rootview",G);
   registerChild("nowbar_card_border_stroke_view",F);
   registerChild("gradient_view",l0);
   registerChild("nudge_container",nudge);
   registerChild("normal_text_container",normalText);
   registerChild("ongoing_card_chronometer_root_view",chronRoot);
   registerChild("ongoing_sec_action_bg",actionBg);
   addView(B);
   addView(actionBg);
  }
  public View getCardViewContainer(){return B;}
  public View getCardBGViewForGuidingEffect(){return x;}
  public View getCardOuterGlowView(){return y;}
  public View getGradientBgView(){return l0;}
  public View getBorderStrokeView(){return F;}
  public float getNormalCardRadius(){return 48f;}
  public boolean getLockscreenViewMode(){return D;}
  public boolean getDozing(){return u;}
  public float getDarkAmount(){return w;}
  public int getCardState(){return U;}
  public boolean isExpanded(){return false;}
  public void B(){bCalls++;}
  public void E(){eCalls++;}
}''',
'com/google/android/material/card/MaterialCardView.java': '''package com.google.android.material.card;
public class MaterialCardView extends android.view.View {}''',
'com/samsung/android/app/facewidget/presentation/NowBarExternalViewCardView.java': '''package com.samsung.android.app.facewidget.presentation;
import android.view.View;
import io.github.tavataan.nowbarcolor.HostTests;
public class NowBarExternalViewCardView extends View {
  public boolean z=true, r=false, Q=false; public float t=0f; public int O=0;
  public Object l0=new Object();
  public HostTests.NativeCard x=new HostTests.NativeCard();
  public View u=new View(), v=new View(), F=new View(), f0=new View();
  public int aCalls=0;
  public NowBarExternalViewCardView(){
   registerChild("nowbar_cardview_background",x);
   registerChild("nowbar_cardview_bgview_for_guidingeffect",u);
   registerChild("nowbar_cardview_guidingeffect_view",v);
   registerChild("nowbar_card_border_stroke_view",F);
   registerChild("gradient_view",f0);
  }
  public View getCardViewContainer(){return x;}
  public View getCardBGViewForGuidingEffect(){return u;}
  public View getCardGuidingEffectView(){return v;}
  public View getBorderStrokeView(){return F;}
  public View getGradientBgView(){return f0;}
  public float getNormalCardRadius(){return 46f;}
  public float getDarkAmount(){return t;}
  public int getCardState(){return O;}
  public void A(){aCalls++;}
}''',
'com/samsung/android/app/facewidget/presentation/NowBarBriefCardView.java': '''package com.samsung.android.app.facewidget.presentation;
import android.view.View;
public class NowBarBriefCardView extends View {
  public boolean D=true, u=false; public float w=0.25f; public int U=0;
  public View B=new View(), G=new View(), briefBg=new View();
  public int bCalls=0, fCalls=0, l0Calls=0, eCalls=0; public float lastDark=-1f;
  public NowBarBriefCardView(){
   registerChild("nowbar_cardview_background",B);
   registerChild("normal_content_rootview",G);
   registerChild("nowbar_cardview_background_for_now_brief",briefBg);
  }
  public View getCardViewContainer(){return B;}
  public float getNormalCardRadius(){return 44f;}
  public float getNormalCardWidth(){return 636f;}
  public float getNormalCardHeight(){return 168f;}
  public boolean getLockscreenViewMode(){return D;}
  public boolean getDozing(){return u;}
  public float getDarkAmount(){return w;}
  public int getCardState(){return U;}
  public boolean isExpanded(){return false;}
  public void B(){bCalls++;}
  public void f(float dark){fCalls++; lastDark=dark;}
  public void l0(){l0Calls++;}
  public void E(){eCalls++;}
}''',
'com/samsung/android/app/facewidget/presentation/NowBarBatteryCardView.java': '''package com.samsung.android.app.facewidget.presentation;
import android.view.View;
import android.view.ViewGroup;
public class NowBarBatteryCardView extends ViewGroup {
  public boolean D=true, u=false, expanded=false; public float w=0.0f; public int U=0, heightPx=600;
  public View B=new View(), F=new View(), m1=new View(), q1=new View();
  public android.view.ViewGroup G=new android.view.ViewGroup();
  public int bCalls=0;
  public NowBarBatteryCardView(){
   registerChild("nowbar_cardview_background",B);
   registerChild("normal_content_rootview",G);
   registerChild("nowbar_card_border_stroke_view",F);
   registerChild("battery_progressbar_container",m1);
   registerChild("battery_progressbar",q1);
   addView(B);
  }
  @Override public int getHeight(){return heightPx;}
  public View getCardViewContainer(){return B;}
  public View getBorderStrokeView(){return F;}
  public float getNormalCardRadius(){return 48f;}
  public boolean getLockscreenViewMode(){return D;}
  public boolean getDozing(){return u;}
  public float getDarkAmount(){return w;}
  public int getCardState(){return U;}
  public boolean isExpanded(){return expanded;}
  public void B(){bCalls++;}
}''',
'android/os/Looper.java': """package android.os; import java.util.ArrayList;
public class Looper { public final ArrayList<Runnable> queue=new ArrayList<>();
public static final Looper MAIN=new Looper(); public static Looper getMainLooper(){return MAIN;}
public static Looper myLooper(){return MAIN;} }""",
'android/os/Handler.java': """package android.os;
public class Handler { private Looper looper; public Handler(Looper l){looper=l;}
public Looper getLooper(){return looper;} public boolean post(Runnable r){looper.queue.add(r);return true;}
public boolean postDelayed(Runnable r,long t){return post(r);} public void removeCallbacks(Runnable r){looper.queue.removeIf(x->x==r);}
public void one(){looper.queue.remove(0).run();} public int pending(){return looper.queue.size();} }""",
'android/os/HandlerThread.java': """package android.os;
public class HandlerThread { private final Looper looper=new Looper();
public HandlerThread(String n,int p){} public void start(){} public Looper getLooper(){return looper;} }""",
'android/os/Bundle.java': """package android.os; public class Bundle {
public String getString(String k,String fallback){return fallback;} }""",
'android/content/Context.java': """package android.content; public class Context {
private java.io.File filesDir;
public void setFilesDir(java.io.File d){filesDir=d;} public java.io.File getFilesDir(){return filesDir;}
public Context getApplicationContext(){return this;} public String getPackageName(){return "com.android.systemui";}
public android.content.res.Resources getResources(){return new android.content.res.Resources();}
public ContentResolver getContentResolver(){return new ContentResolver();} }""",
'android/util/LruCache.java': '''package android.util;
import java.util.*;
public class LruCache<K,V> {
 private final LinkedHashMap<K,V> map;
 public LruCache(int maxSize) { map=new LinkedHashMap<>(16,0.75f,true); }
 protected int sizeOf(K k, V v) { return 1; }
 public synchronized V get(K k) { return map.get(k); }
 public synchronized V put(K k, V v) { return map.put(k, v); }
 public synchronized V remove(K k) { return map.remove(k); }
 public synchronized Map<K,V> snapshot() { return new LinkedHashMap<>(map); }
}''',
'android/net/Uri.java': '''package android.net;
import java.util.*;
public class Uri {
 private final String raw;
 protected Uri(String s){this.raw=s;}
 public static Uri parse(String s){return new Uri(s==null?"":s);}
 public String toString(){return raw;}
 public List<String> getPathSegments(){
  int scheme=raw.indexOf("://");
  String rest=scheme>=0?raw.substring(scheme+3):raw;
  int slash=rest.indexOf('/');
  if(slash<0)return Collections.emptyList();
  String path=rest.substring(slash+1);
  if(path.isEmpty())return Collections.emptyList();
  return Arrays.asList(path.split("/"));
 }
}''',
'android/content/ContentResolver.java': """package android.content; public class ContentResolver {
public android.os.Bundle call(android.net.Uri u,String m,String a,android.os.Bundle b){return new android.os.Bundle();}
public android.os.ParcelFileDescriptor openFileDescriptor(android.net.Uri u,String m) throws java.io.FileNotFoundException {return null;} }""",
'android/content/res/Configuration.java': '''package android.content.res;
public class Configuration { public static final int UI_MODE_NIGHT_MASK=48, UI_MODE_NIGHT_YES=32; public int uiMode=16; }''',
'android/content/res/Resources.java': '''package android.content.res;
import android.util.DisplayMetrics;
import java.util.HashMap;
public class Resources { public final Configuration config=new Configuration(); public final DisplayMetrics metrics=new DisplayMetrics(); {metrics.density=3.0f;}
 public Configuration getConfiguration(){return config;} public DisplayMetrics getDisplayMetrics(){return metrics;}
 private static final HashMap<String,Integer> names=new HashMap<>(); private static final HashMap<Integer,String> rev=new HashMap<>(); private static int nextId=0x7d0a0100;
 public synchronized int registerId(String n){Integer cur=names.get(n); if(cur!=null)return cur; int id=nextId++; names.put(n,id); rev.put(id,n); return id;}
 public synchronized int getIdentifier(String n,String d,String p){Integer cur=names.get(n); return cur!=null?cur:0;}
 public synchronized String getResourceEntryName(int id){String s=rev.get(id); return s!=null?s:""; } }''',
'android/provider/Settings.java': """package android.provider; public class Settings {
public static class System { public static int reduce;
 public static String getString(android.content.ContentResolver r,String k){return "{\\\"masterEnabled\\\":false}";}
 public static int getInt(android.content.ContentResolver r,String k,int d){return "accessibility_reduce_transparency".equals(k)?reduce:d;} }
public static class Global { public static String getString(android.content.ContentResolver r,String k){return null;} } }""",
'android/util/Log.java': """package android.util; public class Log { public static int e(String t,String m,Throwable e){throw new AssertionError(m,e);} }""",
'org/json/JSONObject.java': '''package org.json;
import java.util.LinkedHashMap;
import java.util.Map;
public class JSONObject {
 private String raw;
 private final LinkedHashMap<String,Object> map = new LinkedHashMap<>();
 public JSONObject(){}
 public JSONObject(String json) throws JSONException {
  this.raw=json;
  if(json!=null){
   String body=json.trim();
   if(body.startsWith("{") && body.endsWith("}")) body=body.substring(1,body.length()-1);
   int i=0, n=body.length();
   while(i<n){
    while(i<n && (body.charAt(i)==' '||body.charAt(i)==',')) i++;
    if(i>=n || body.charAt(i)!='"') break;
    int kEnd=body.indexOf('"', i+1); if(kEnd<0) break;
    String k=body.substring(i+1,kEnd);
    int colon=body.indexOf(':', kEnd+1); if(colon<0) break;
    i=colon+1; while(i<n && body.charAt(i)==' ') i++;
    if(i<n && body.charAt(i)=='"'){
     int vEnd=body.indexOf('"', i+1); if(vEnd<0) break;
     map.put(k, body.substring(i+1,vEnd)); i=vEnd+1;
    } else {
     int vEnd=i; while(vEnd<n && body.charAt(vEnd)!=',') vEnd++;
     String tok=body.substring(i,vEnd).trim();
     if("true".equals(tok)||"false".equals(tok)) map.put(k, Boolean.valueOf(tok));
     else { try{ map.put(k, Long.valueOf(tok)); }catch(Exception e){ map.put(k, tok); } }
     i=vEnd+1;
    }
   }
  }
 }
 public boolean optBoolean(String name,boolean fallback){
  Object v=map.get(name); if(v instanceof Boolean) return (Boolean)v;
  if(raw!=null && raw.contains("\\""+name+"\\":false"))return false;
  if(raw!=null && raw.contains("\\""+name+"\\":true"))return true;
  return fallback;
 }
 public int optInt(String name,int fallback){
  Object v=map.get(name); return v instanceof Number ? ((Number)v).intValue() : fallback;
 }
 public long optLong(String name,long fallback){
  Object v=map.get(name); return v instanceof Number ? ((Number)v).longValue() : fallback;
 }
 public String optString(String name,String fallback){
  Object v=map.get(name); return v instanceof String ? (String)v : fallback;
 }
 public JSONObject optJSONObject(String name){return null;}
 public JSONObject put(String name,boolean value) throws JSONException {map.put(name,value);return this;}
 public JSONObject put(String name,int value) throws JSONException {map.put(name,value);return this;}
 public JSONObject put(String name,long value) throws JSONException {map.put(name,value);return this;}
 public JSONObject put(String name,Object value) throws JSONException {map.put(name,value);return this;}
 @Override public String toString(){
  StringBuilder sb=new StringBuilder("{"); boolean first=true;
  for(Map.Entry<String,Object> e:map.entrySet()){
   if(!first)sb.append(','); first=false;
   sb.append('"').append(e.getKey()).append("\\":");
   Object v=e.getValue();
   if(v instanceof Number||v instanceof Boolean)sb.append(v);
   else sb.append('"').append(String.valueOf(v)).append('"');
  }
  return sb.append('}').toString();
 }
}''',
'android/content/res/ColorStateList.java': '''package android.content.res;
public class ColorStateList { private int color; public ColorStateList(int c){color=c;}
public static ColorStateList valueOf(int c){return new ColorStateList(c);}
public boolean isStateful(){return false;} public int getDefaultColor(){return color;} }''',
'android/util/DisplayMetrics.java': 'package android.util; public class DisplayMetrics { public float density=3.0f; }',
'android/graphics/Rect.java': '''package android.graphics;
public class Rect {
 public int left, top, right, bottom;
 public Rect(){} public Rect(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;}
 public int width(){return right-left;} public int height(){return bottom-top;}
 public boolean isEmpty(){return left>=right||top>=bottom;}
}''',
'android/graphics/ColorFilter.java': 'package android.graphics; public class ColorFilter {}',
'android/graphics/RectF.java': '''package android.graphics;
public class RectF {
 public float left, top, right, bottom;
 public RectF(){}
 public RectF(float l,float t,float r,float b){left=l;top=t;right=r;bottom=b;}
 public void set(Rect r){if(r!=null){left=r.left;top=r.top;right=r.right;bottom=r.bottom;}}
 public void set(float l,float t,float r,float b){left=l;top=t;right=r;bottom=b;}
}''',
'android/graphics/Matrix.java': '''package android.graphics;
public class Matrix {
 public void setScale(float sx,float sy){}
 public boolean postTranslate(float dx,float dy){return true;}
}''',
'android/graphics/Shader.java': '''package android.graphics;
public class Shader {
 public enum TileMode { CLAMP, REPEAT, MIRROR }
 public void setLocalMatrix(Matrix localM){}
}''',
'android/graphics/LinearGradient.java': '''package android.graphics;
public class LinearGradient extends Shader {
 public LinearGradient(float x0,float y0,float x1,float y1,int[] colors,float[] positions,TileMode tile){}
 public LinearGradient(float x0,float y0,float x1,float y1,int color0,int color1,TileMode tile){}
}''',
'android/graphics/BitmapShader.java': '''package android.graphics;
public class BitmapShader extends Shader {
 public BitmapShader(Bitmap bmp,TileMode tx,TileMode ty){}
}''',
'android/graphics/Paint.java': '''package android.graphics;
public class Paint {
 public static final int ANTI_ALIAS_FLAG=1, FILTER_BITMAP_FLAG=2;
 public enum Style { FILL, STROKE, FILL_AND_STROKE }
 private Style style=Style.FILL; private Shader shader; private int color, alpha=255; private float strokeWidth; private ColorFilter cf;
 public Paint(){}
 public Paint(int flags){}
 public void setStyle(Style s){style=s;}
 public void setStrokeWidth(float w){strokeWidth=w;}
 public float getStrokeWidth(){return strokeWidth;}
 public Shader setShader(Shader s){shader=s;return s;}
 public Shader getShader(){return shader;}
 public void setColor(int c){color=c;}
 public int getColor(){return color;}
 public void setAlpha(int a){alpha=a;}
 public int getAlpha(){return alpha;}
 public ColorFilter setColorFilter(ColorFilter f){cf=f;return f;}
}''',
'android/graphics/Canvas.java': '''package android.graphics;
public class Canvas {
 public void drawRoundRect(RectF r,float rx,float ry,Paint p){}
}''',
'android/graphics/drawable/Drawable.java': '''package android.graphics.drawable;
import android.content.res.ColorStateList; import android.graphics.*;
public class Drawable {
 private int alpha=255; private boolean visible=true; private ColorFilter filter; private ColorStateList tint;
 private final Rect bounds=new Rect();
 public Rect getBounds(){return bounds;} public void draw(Canvas c){} public void invalidateSelf(){}
 public int getAlpha(){return alpha;} public void setAlpha(int a){alpha=a;}
 public boolean isVisible(){return visible;} public boolean setVisible(boolean v,boolean r){boolean changed=v!=visible;visible=v;return changed;}
 public void setTintList(ColorStateList t){tint=t;} public ColorStateList inspectTint(){return tint;}
 public void setTint(int c){setTintList(new ColorStateList(c));}
 public void setTintMode(PorterDuff.Mode m){} public void setTintBlendMode(BlendMode m){}
 public void setColorFilter(ColorFilter f){filter=f;} public ColorFilter getColorFilter(){return filter;}
 public void clearColorFilter(){setColorFilter(null);}
 protected void onBoundsChange(Rect bounds){}
}''',
'android/graphics/drawable/GradientDrawable.java': '''package android.graphics.drawable;
import android.content.res.ColorStateList;
public class GradientDrawable extends Drawable {
 public enum Orientation { TOP_BOTTOM, LEFT_RIGHT }
 private int[] colors; private float radius; private ColorStateList color;
 private Orientation orientation=Orientation.TOP_BOTTOM;
 public static final int RECTANGLE=0,LINEAR_GRADIENT=0;
 public int[] getColors(){return colors==null?null:colors.clone();}
 public void setColors(int[] c){setColors(c,null);} public void setColors(int[] c,float[] offsets){colors=c;color=null;}
 public void setShape(int s){} public void setGradientType(int t){}
 public Orientation getOrientation(){return orientation;} public void setOrientation(Orientation o){orientation=o;}
 public float getCornerRadius(){return radius;} public void setCornerRadius(float r){radius=r;}
 public ColorStateList getColor(){return color;} public void setColor(int c){setColor(new ColorStateList(c));} public void setColor(ColorStateList c){color=c;colors=null;}
}''',
'android/view/ViewParent.java': 'package android.view; public interface ViewParent { ViewParent getParent(); }',
'android/view/ViewTreeObserver.java': '''package android.view;
import java.util.ArrayList;
public class ViewTreeObserver {
 public interface OnPreDrawListener { boolean onPreDraw(); }
 public boolean alive=true; public int adds=0;
 public final ArrayList<OnPreDrawListener> listeners=new ArrayList<>();
 public boolean isAlive(){return alive;}
 public void addOnPreDrawListener(OnPreDrawListener l){adds++;listeners.add(l);}
 public void removeOnPreDrawListener(OnPreDrawListener l){listeners.remove(l);}
 public void draw(){for(OnPreDrawListener l:new ArrayList<>(listeners))if(!l.onPreDraw())throw new AssertionError("draw blocked");}
}''',
'android/view/View.java': '''package android.view;
import android.content.Context; import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import java.util.ArrayList;
public class View implements ViewParent {
 public interface OnAttachStateChangeListener { void onViewAttachedToWindow(View v); void onViewDetachedFromWindow(View v); }
 private final ArrayList<OnAttachStateChangeListener> attach=new ArrayList<>();
 private ViewTreeObserver tree=new ViewTreeObserver();
 public Resources resources=new Resources(); private final Context context=new Context();
 private final java.util.HashMap<Integer,View> idViews=new java.util.HashMap<>();
 public ViewParent parent;
 public static final int VISIBLE=0,INVISIBLE=4,GONE=8,NO_ID=-1;
 private int viewId=NO_ID;
 private int visibility=VISIBLE; private float alpha=1f, elevation=6f;
 public void registerChild(String entry, View child){
  int id=resources.registerId(entry);
  if(child!=null){child.viewId=id; child.resources=resources; idViews.put(id,child);}
 }
 public int getId(){return viewId;} public View findViewById(int id){return idViews.get(id);}
 public int getVisibility(){return visibility;} public void setVisibility(int v){visibility=v;}
 public int getWidth(){return 100;} public int getHeight(){return 48;}
 public float getAlpha(){return alpha;} public void setAlpha(float a){alpha=a;}
 public float getElevation(){return elevation;} public void setElevation(float e){elevation=e;}
 private Drawable fg;
 public Drawable getForeground(){return fg;} public void setForeground(Drawable d){fg=d;}
 public Resources getResources(){return resources;}
 public Context getContext(){return context;}
 public ViewParent getParent(){return parent;}
 public boolean failGetter; public boolean attached=true; public int invalidations, backgrounds, blurSets; private Drawable bg; private final Drawable blurBg=new Drawable(); private boolean clip;
 private SemBlurInfo blur;
 public void addOnAttachStateChangeListener(OnAttachStateChangeListener l){attach.add(l);}
 public ViewTreeObserver getViewTreeObserver(){return tree;}
 public boolean isAttachedToWindow(){return attached;}
 public void invalidate(){invalidations++;}
 private android.content.res.ColorStateList backgroundTint;
 public android.content.res.ColorStateList getBackgroundTintList(){return backgroundTint;}
 public void setBackgroundTintList(android.content.res.ColorStateList t){backgroundTint=t;if(bg!=null)bg.setTintList(t);}
 public Drawable getBackground(){return bg;} public void setBackground(Drawable d){backgrounds++;bg=d;if(bg!=null)bg.setTintList(backgroundTint);}
 public boolean getClipToOutline(){return clip;} public void setClipToOutline(boolean b){clip=b;}
 public void semSetBlurInfo(SemBlurInfo b){blurSets++;blur=b;if(b!=null)bg=blurBg;else if(bg==blurBg)bg=null;}
 public SemBlurInfo semGetBlurInfo(){if(failGetter)throw new IllegalStateException();return blur;}
 public SemBlurInfo inspectBlur(){return blur;}
 public void detach(){attached=false;for(OnAttachStateChangeListener l:attach)l.onViewDetachedFromWindow(this);tree.alive=false;}
 public void attach(){tree=new ViewTreeObserver();attached=true;for(OnAttachStateChangeListener l:attach)l.onViewAttachedToWindow(this);}
}''',
'android/view/SemBlurInfo.java': '''package android.view;
public class SemBlurInfo {
 public static int builds;
 public int mode, radius, bgColor; public float corner, sat, curve, minX, maxX, minY, maxY;
 public static class Builder {
  private final SemBlurInfo info=new SemBlurInfo(); private boolean badPreset;
  public Builder(int mode){info.mode=mode;}
  public Builder setColorCurvePreset(int v){if(v==54)badPreset=true;return this;}
  public Builder setRadius(int r){info.radius=r;return this;}
  public Builder setColorCurve(float s,float c,float x0,float x1,float y0,float y1){
   info.sat=s;info.curve=c;info.minX=x0;info.maxX=x1;info.minY=y0;info.maxY=y1;return this;
  }
  public Builder setBackgroundCornerRadius(float v){info.corner=v;return this;}
  public Builder setBackgroundColor(int c){info.bgColor=c;return this;}
  public SemBlurInfo build(){if(badPreset)throw new IllegalStateException("Invalid preset");builds++;return info;}
 }
}''',
'android/view/ViewGroup.java': '''package android.view;
import java.util.ArrayList;
public class ViewGroup extends View {
 private final ArrayList<View> children=new ArrayList<>();
 public void addView(View v){children.add(v);}
 public int getChildCount(){return children.size();} public View getChildAt(int i){return children.get(i);} }''',
'android/widget/TextView.java': '''package android.widget;
import android.content.res.ColorStateList;
public class TextView extends android.view.View {
 private ColorStateList textColors=ColorStateList.valueOf(0xFFFFFFFF);
 public float lastShadowRadius; public int lastShadowColor;
 public CharSequence getText(){return "";}
 public ColorStateList getTextColors(){return textColors;}
 public void setTextColor(int c){textColors=ColorStateList.valueOf(c);}
 public void setTextColor(ColorStateList c){if(c!=null)textColors=c;}
 public void setShadowLayer(float r,float dx,float dy,int c){lastShadowRadius=r;lastShadowColor=c;} }''',
'android/widget/ImageView.java': '''package android.widget;
import android.content.res.ColorStateList;
public class ImageView extends android.view.View {
 private ColorStateList imageTint;
 public ColorStateList getImageTintList(){return imageTint;}
 public void setImageTintList(ColorStateList t){imageTint=t;} }''',
'de/robv/android/xposed/XposedHelpers.java': '''package de.robv.android.xposed;
import java.util.WeakHashMap; import java.util.HashMap;
public class XposedHelpers {
 private static final WeakHashMap<Object,HashMap<String,Object>> fields=new WeakHashMap<>();
 public static int restorationReads;
 public static Object getAdditionalInstanceField(Object o,String k){
  if(k.equals("nb_custom_applied"))restorationReads++;
  return fields.containsKey(o)?fields.get(o).get(k):null;
 }
 public static Object setAdditionalInstanceField(Object o,String k,Object v){
  if(!fields.containsKey(o))fields.put(o,new HashMap<String,Object>());
  return fields.get(o).put(k,v);
 }
}''',
'io/github/tavataan/nowbarcolor/HostTests.java': '''package io.github.tavataan.nowbarcolor;
import android.view.*; import android.graphics.drawable.*;
import android.content.res.ColorStateList;
import java.lang.reflect.*;
import de.robv.android.xposed.XposedHelpers;
public class HostTests {
 static int checks;
 static Field hookField(String name)throws Exception {Field f=NowBarHook.class.getDeclaredField(name);f.setAccessible(true);return f;}
 static void checkGradient(View v,int first,int second,String label) {
  check(v.getBackground() instanceof GradientDrawable,label+": drawable exists");
  GradientDrawable g=(GradientDrawable)v.getBackground(); int[] colors=g.getColors();
  check(colors!=null && colors.length==2 && colors[0]==first && colors[1]==second,label+": palette/opacity intact");
  check(g.getAlpha()==255 && g.isVisible() && g.getColorFilter()==null && g.inspectTint()==null && v.getBackgroundTintList()==null,label+": drawable actually visible");
 }
 static void checkForegroundGradient(View v,int first,int second,String label) {
  check(v.getBackground()!=null && !(v.getBackground() instanceof GradientDrawable),label+": native BackgroundBlurDrawable preserved on mBackground");
  check(v.getForeground() instanceof GradientDrawable,label+": foreground gradient drawable exists");
  GradientDrawable g=(GradientDrawable)v.getForeground(); int[] colors=g.getColors();
  check(colors!=null && colors.length==2 && colors[0]==first && colors[1]==second,label+": palette/opacity intact");
  check(g.getAlpha()==255 && g.isVisible() && g.getColorFilter()==null && g.inspectTint()==null && v.getBackgroundTintList()==null,label+": foreground drawable actually visible");
 }
 static void check(boolean b,String label){if(!b)throw new AssertionError(label);checks++;}
 public static class Parent { private int value=7; public int number(){return 8;} public int number(int x){return x;} }
 public static class Child extends Parent {}
 public static class NativeCard extends View {
  int color; int sets; boolean throwGetter; public void setCardBackgroundColor(int c){color=c;sets++;}
  public ColorStateList getCardBackgroundColor(){if(throwGetter)throw new IllegalStateException();return new ColorStateList(color);}
 }
 public static class RoundRectDrawable extends Drawable {
  int paintColor;
  public void setAlpha(int a){super.setAlpha(a); paintColor=(paintColor & 0x00ffffff)|((a & 0xff)<<24);}
 }
 public static class RoundRectCard extends NativeCard {
  final RoundRectDrawable nativeRoundRect=new RoundRectDrawable();
  public RoundRectCard(){setBackground(nativeRoundRect);}
  public void setCardBackgroundColor(int c){super.setCardBackgroundColor(c); nativeRoundRect.paintColor=c;}
 }
 public static void main(String[] args)throws Exception{
  boolean getter=Boolean.parseBoolean(args[0]);
  Child child=new Child();
  Field field=ReflectionCache.field(Child.class,"value");
  check(field.getInt(child)==7,"inherited private field");
  check(field==ReflectionCache.field(Child.class,"value"),"field metadata cached");
  for(int i=0;i<100;i++)check(ReflectionCache.field(Child.class,"absent")==null,"missing field");
  check(ReflectionCache.method(Child.class,"number").invoke(child).equals(8),"getter invoked");
  check(ReflectionCache.method(Child.class,"number",int.class).invoke(child,9).equals(9),"overloads distinct");
  check(ReflectionCache.method(Child.class,"absent")==null,"missing method");

  View reused=new View(); CapsuleStyle.gradient(reused,0x80112233,0x80445566,40);
  Drawable reusedIdentity=reused.getBackground(); reusedIdentity.setAlpha(0);
  CapsuleStyle.gradient(reused,0x80112233,0x80445566,40);
  checkGradient(reused,0x80112233,0x80445566,"reused zero-alpha gradient repaired");
  check(reused.getBackground()==reusedIdentity,"alpha repair keeps drawable identity");
  reusedIdentity.setTint(0); CapsuleStyle.gradient(reused,0x80112233,0x80445566,40);
  checkGradient(reused,0x80112233,0x80445566,"transparent drawable tint repaired");
  reusedIdentity.setColorFilter(new android.graphics.ColorFilter());
  reusedIdentity.setVisible(false,false); CapsuleStyle.gradient(reused,0x80112233,0x80445566,40);
  checkGradient(reused,0x80112233,0x80445566,"filter and drawable visibility repaired");
  ((GradientDrawable)reusedIdentity).setColor(0); CapsuleStyle.gradient(reused,0x80112233,0x80445566,40);
  checkGradient(reused,0x80112233,0x80445566,"native single-color rewrite repaired");
  View tinted=new View(); Drawable tintedOriginal=new Drawable(); tinted.setBackground(tintedOriginal);
  ColorStateList originalTint=new ColorStateList(0x55334455);tinted.setBackgroundTintList(originalTint);
  CapsuleStyle.gradient(tinted,0x80112233,0x80445566,40);
  checkGradient(tinted,0x80112233,0x80445566,"view tint suspended while gradient active");
  CapsuleStyle.restoreGradient(tinted);
  check(tinted.getBackground()==tintedOriginal && tinted.getBackgroundTintList()==originalTint,"native drawable and tint restored");
  CapsuleStyle.gradient(reused,0x00112233,0x00445566,40);
  checkGradient(reused,0x00112233,0x00445566,"intentional zero opacity preserved");
  View view=new View(); Drawable original=new Drawable(); view.setBackground(original);
  CapsuleStyle.gradient(view,11,22,30); Drawable gradient=view.getBackground(); int writes=view.backgrounds;
  for(int i=0;i<100;i++)CapsuleStyle.gradient(view,11,22,30);
  check(view.getBackground()==gradient && view.backgrounds==writes,"stable gradient reused without setBackground");
  CapsuleStyle.gradient(view,33,44,40);
  check(view.getBackground()==gradient && ((GradientDrawable)gradient).getCornerRadius()==40,"gradient/radius update in place");
  ((GradientDrawable)gradient).setColors(new int[]{99,99});
  CapsuleStyle.gradient(view,33,44,40);
  check(((GradientDrawable)gradient).getColors()[0]==33,"native mutation repaired");
  view.setBackground(new Drawable()); CapsuleStyle.gradient(view,33,44,40);
  check(view.getBackground()==gradient,"native replacement repaired");
  CapsuleStyle.restoreGradient(view);
  check(view.getBackground()==original && !view.getClipToOutline(),"original background/clip restored");
  Drawable nextOriginal=new Drawable(); view.setBackground(nextOriginal);
  CapsuleStyle.gradient(view,1,2,30); CapsuleStyle.restoreGradient(view);
  check(view.getBackground()==nextOriginal,"new native background captured after restore");
  CapsuleStyle.solid(view,0xff123456,50); Drawable solid=view.getBackground(); writes=view.backgrounds;
  for(int i=0;i<100;i++)CapsuleStyle.solid(view,0xff123456,50);
  check(view.getBackground()==solid && view.backgrounds==writes,"solid drawable reused");
  CapsuleStyle.gradient(view,1,2,30); CapsuleStyle.solid(view,0xff654321,25);
  check(view.getBackground()==solid && ((GradientDrawable)solid).getColor().getDefaultColor()==0xff654321,"gradient to solid");
  CapsuleStyle.solid(view,0,30); check(view.getBackground()==null,"transparent clears generic background");
  Drawable nativeOriginal=new Drawable(); NativeCard nativeCard=new NativeCard(); nativeCard.setBackground(nativeOriginal);
  CapsuleStyle.solid(nativeCard,0x80123456,30); Drawable nativeSolid=nativeCard.getBackground();
  int nativeWrites=nativeCard.backgrounds;
  for(int i=0;i<100;i++)CapsuleStyle.solid(nativeCard,0x80123456,30);
  check(nativeCard.sets==1 && nativeCard.color==0,"solid clears native CardView backing once");
  check(nativeCard.backgrounds==nativeWrites && nativeSolid instanceof GradientDrawable && ((GradientDrawable)nativeSolid).getColor().getDefaultColor()==0x80123456,"solid GradientDrawable reused across identical calls with ARGB alpha intact");
  nativeSolid.setAlpha(128);
  check(CapsuleStyle.needsGradientRepair(nativeCard),"needsGradientRepair detects altered alpha on solid drawable");
  CapsuleStyle.solid(nativeCard,0x80123456,30);
  check(nativeSolid.getAlpha()==255,"solid alpha fade mutation repaired while preserving ARGB fill");
  nativeCard.setBackground(null);
  check(CapsuleStyle.needsGradientRepair(nativeCard),"needsGradientRepair detects detached solid background");
  CapsuleStyle.solid(nativeCard,0x80123456,30);
  check(nativeCard.getBackground()==nativeSolid,"detached solid background is repaired");
  CapsuleStyle.solid(nativeCard,0x80445566,30);
  check(((GradientDrawable)nativeCard.getBackground()).getColor().getDefaultColor()==0x80445566,"solid GradientDrawable tracks later color updates");
  CapsuleStyle.restoreGradient(nativeCard);
  check(nativeCard.getBackground()==nativeOriginal,"restoreGradient restores original native background on CardView");
  int builds=SemBlurInfo.builds;
  check(CapsuleStyle.blur(view,30,42),"blur supported"); int sets=view.blurSets;
  for(int i=0;i<100;i++)CapsuleStyle.blur(view,30,42);
  check(SemBlurInfo.builds==builds+1,"one blur build for unchanged style");
  check(!getter || view.blurSets==sets,"live blur getter avoids duplicate setter");
  view.semSetBlurInfo(new SemBlurInfo()); CapsuleStyle.blur(view,30,42);
  check(view.blurSets==(getter?sets+2:sets+102),"native blur replacement repaired");
  CapsuleStyle.blur(view,31,42); check(SemBlurInfo.builds==builds+2,"radius invalidates blur");
  CapsuleStyle.clearBlur(view); sets=view.blurSets; CapsuleStyle.clearBlur(view);
  check(!getter || view.blurSets==sets,"already clear blur skipped only with getter");
  CapsuleStyle.blur(view,31,42); check(SemBlurInfo.builds==builds+2,"re-enable reuses blur object");

  // OneUiBlurSpec / CapsuleStyle.glass checks (before failingGetter flips static blurGetterFailed)
  View singleGlass=new View();
  CapsuleStyle.glass(singleGlass,0xeb1477ff,0xeb1477ff,42f);
  check(singleGlass.inspectBlur()!=null && singleGlass.inspectBlur().radius==92 && singleGlass.inspectBlur().bgColor==0xcb1477ff,"single-color glass tints SemBlurInfo directly within recalibrated 16-100px range");
  check(singleGlass.getBackground()!=null && !(singleGlass.getBackground() instanceof GradientDrawable) && singleGlass.getForeground() instanceof GradientDrawable,"single-color glass preserves BackgroundBlurDrawable and adds transparent glass sheen foreground");
  CapsuleStyle.glass(singleGlass,0x261477ff,0x261477ff,42f);
  check(singleGlass.inspectBlur().radius==16 && singleGlass.inspectBlur().bgColor==0x401477ff,"min slider sets 16px blur radius and 25% min tint alpha");
  View glassView=new View();
  CapsuleStyle.glass(glassView,0x99ffea00,0x999d4edd,66,42f,1.0f,true);
  check(glassView.inspectBlur()!=null && glassView.inspectBlur().radius==66 && glassView.inspectBlur().bgColor==0,"decoupled 2-stop glass applies 66px blur at default 60%");
  check(glassView.inspectBlur().minY==16.0f && glassView.inspectBlur().maxY==236.0f && glassView.inspectBlur().sat==0.38f && glassView.inspectBlur().curve==0.0f,"glass uses vivid +0.38 saturation linear color curve in light mode");
  checkForegroundGradient(glassView,0x99ffea00,0x999d4edd,"decoupled 2-stop glass preserves 60% (0x99) tint opacity while keeping BackgroundBlurDrawable");
  glassView.getResources().config.uiMode=32;
  CapsuleStyle.glass(glassView,0xd9ffea00,0xd99d4edd,100,42f,1.0f,true);
  check(glassView.inspectBlur().radius==100 && glassView.inspectBlur().minY==12.0f && glassView.inspectBlur().maxY==226.0f,"100% blur sets 100px max radius with vivid dark curve (12..226)");
  checkForegroundGradient(glassView,0xd9ffea00,0xd99d4edd,"max 85% tint opacity preserves vivid 0xd9 alpha");
  View nestedGlass=new View(); nestedGlass.parent=glassView;
  CapsuleStyle.glass(nestedGlass,0xff112233,0xff445566,42f);
  check(nestedGlass.inspectBlur()==null,"nested hosts do not stack blur material");
  CapsuleStyle.releaseBlur(glassView);
  check(!getter || glassView.inspectBlur()==null,"releaseBlur clears owned blur");

  View failingGetter=new View();failingGetter.failGetter=true;
  check(CapsuleStyle.blur(failingGetter,30,42),"throwing getter falls back to setter");
  int beforeFallback=failingGetter.blurSets;
  check(CapsuleStyle.blur(failingGetter,30,42) && failingGetter.blurSets==beforeFallback+1,"failed getter uses conservative reapply");
  Field blurGetterFailedField=CapsuleStyle.class.getDeclaredField("blurGetterFailed");
  blurGetterFailedField.setAccessible(true);
  blurGetterFailedField.setBoolean(null, false);

  Field config=NowBarHook.class.getDeclaredField("sConfig");config.setAccessible(true);
  hookField("sAdapter").set(null, OneUi9Adapter.INSTANCE);
  ConfigManager.Config disabled=new ConfigManager.Config();disabled.masterEnabled=false;config.set(null,disabled);
  Class<?> scheduler=Class.forName("io.github.tavataan.nowbarcolor.NowBarHook$CardUpdate");
  Constructor<?> constructor=scheduler.getDeclaredConstructor(View.class);constructor.setAccessible(true);
  Method request=scheduler.getDeclaredMethod("request");request.setAccessible(true);
  View card=new View(); Object update=constructor.newInstance(card);
  for(int i=0;i<100;i++)request.invoke(update);
  check(card.getViewTreeObserver().listeners.size()==1 && card.invalidations==1,"100 requests coalesced");
  int reads=XposedHelpers.restorationReads; card.getViewTreeObserver().draw();
  check(XposedHelpers.restorationReads==reads+1,"one production apply for batch");
  check(card.getViewTreeObserver().listeners.isEmpty(),"no idle polling");
  request.invoke(update); ViewTreeObserver old=card.getViewTreeObserver(); card.detach();
  check(old.listeners.isEmpty(),"detach removes pending listener");
  request.invoke(update);check(old.listeners.isEmpty(),"detached requests not queued");
  card.attach();check(card.getViewTreeObserver().listeners.size()==1,"reattach schedules first draw");
  card.getViewTreeObserver().draw();check(card.getViewTreeObserver().listeners.isEmpty(),"reattach drains once");
  Field applying=scheduler.getDeclaredField("applying");applying.setAccessible(true);applying.setBoolean(update,true);
  request.invoke(update);check(card.getViewTreeObserver().listeners.isEmpty(),"reentrant update suppressed");
  applying.setBoolean(update,false);request.invoke(update);card.getViewTreeObserver().draw();
  check(card.getViewTreeObserver().listeners.isEmpty(),"next update accepted after reentry");
  for(int mode=0;mode<4;mode++) {
   ConfigManager.Config mediaConfig=new ConfigManager.Config();
   mediaConfig.styleMode=(mode%2==1)?ConfigManager.STYLE_BLUR:ConfigManager.STYLE_SOLID;
   mediaConfig.colorMode=(mode>=2)?ConfigManager.MODE_GRADIENT:ConfigManager.MODE_FIXED_COLOR;
   config.set(null,mediaConfig);
   com.samsung.android.app.facewidget.presentation.NowBarMediaCardView music=
    new com.samsung.android.app.facewidget.presentation.NowBarMediaCardView();
   Object mediaUpdate=constructor.newInstance(music); request.invoke(mediaUpdate);
   music.getViewTreeObserver().draw();
   check(music.p1.getBackground()!=null && music.o1.getBackground()==null,"initial media background on p1 only, mode="+mode);
   for(int cycle=0;cycle<5;cycle++) {
    music.l0=true; music.j0=1; music.getViewTreeObserver().draw();
    music.p1.setVisibility(View.GONE);music.o1.setVisibility(View.VISIBLE);
    music.l0=false;music.j0=0;music.getViewTreeObserver().draw();
    check(music.o1.getBackground()==null && music.o1.inspectBlur()==null,"o1 stays transparent during collapse, mode="+mode);
    // Samsung restores p1 visibility after the collapse fade; o1 must remain transparent.
    music.p1.setVisibility(View.VISIBLE);music.getViewTreeObserver().draw();
    check(music.p1.getBackground()!=null && music.o1.getBackground()==null,"late layer switch repaired on p1, mode="+mode);
    music.p1.setBackground(null);music.getViewTreeObserver().draw();
    check(music.p1.getBackground()!=null && music.o1.getBackground()==null,"late native background removal repaired, mode="+mode);
   }
   if(mode>=2) {
    boolean isBlur=(mode%2==1);
    int[] expected=isBlur
     ? mediaConfig.resolveBlurGradientArgbForCategory(ConfigManager.CAT_MEDIA)
     : mediaConfig.resolveGradientArgbForCategory(ConfigManager.CAT_MEDIA);
    int e0=expected[0];
    int e1=expected[1];
    Drawable stableGradient=isBlur?music.p1.getForeground():music.p1.getBackground();
    stableGradient.setAlpha(0);music.getViewTreeObserver().draw();
    if(isBlur)checkForegroundGradient(music.p1,e0,e1,"late alpha mutation without hook");
    else checkGradient(music.p1,e0,e1,"late alpha mutation without hook");
    check((isBlur?music.p1.getForeground():music.p1.getBackground())==stableGradient,"late repair reuses original gradient");
    stableGradient.setTint(0);music.getViewTreeObserver().draw();
    if(isBlur)checkForegroundGradient(music.p1,e0,e1,"late transparent tint without hook");
    else checkGradient(music.p1,e0,e1,"late transparent tint without hook");
    ((GradientDrawable)stableGradient).setColor(0);music.getViewTreeObserver().draw();
    if(isBlur)checkForegroundGradient(music.p1,e0,e1,"late transparent color without hook");
    else checkGradient(music.p1,e0,e1,"late transparent color without hook");
    music.p1.setBackgroundTintList(new ColorStateList(0));music.getViewTreeObserver().draw();
    if(isBlur)checkForegroundGradient(music.p1,e0,e1,"late view tint without hook");
    else checkGradient(music.p1,e0,e1,"late view tint without hook");
    int identicalFrames=music.p1.backgrounds+music.p1.blurSets;
    for(int unchanged=0;unchanged<100;unchanged++) {
     stableGradient.setTintList(null);stableGradient.setColorFilter(null);stableGradient.setAlpha(255);
     music.getViewTreeObserver().draw();
    }
    check(music.p1.backgrounds+music.p1.blurSets==identicalFrames,"unchanged native setters do not restyle gradient");
    for(int transition=0;transition<5;transition++) {
     music.l0=true;music.j0=1;music.getViewTreeObserver().draw();
     stableGradient.setAlpha(0);stableGradient.setTint(0);
     music.l0=false;music.j0=0;music.getViewTreeObserver().draw();
     if(isBlur)checkForegroundGradient(music.p1,e0,e1,"collapse after mutation while expanded");
     else checkGradient(music.p1,e0,e1,"collapse after mutation while expanded");
    }
   }
   int invalidations=music.invalidations;
   int backgrounds=music.p1.backgrounds+music.o1.backgrounds;
   int blurWrites=music.p1.blurSets+music.o1.blurSets;
   for(int frame=0;frame<100;frame++)music.getViewTreeObserver().draw();
   check(music.invalidations==invalidations,"media observer schedules no idle frames");
   check(music.p1.backgrounds+music.o1.backgrounds==backgrounds && music.p1.blurSets+music.o1.blurSets==blurWrites,"stable frames do not restyle media");
   for(int event=0;event<100;event++)request.invoke(mediaUpdate);
   check(music.invalidations==invalidations+1,"persistent media observer still coalesces invalidations");
   music.getViewTreeObserver().draw();
   ViewTreeObserver mediaTree=music.getViewTreeObserver();music.detach();
   check(mediaTree.listeners.isEmpty(),"media listener removed on detach");
   music.attach();music.getViewTreeObserver().draw();
   check(music.getViewTreeObserver().listeners.size()==1 && music.p1.getBackground()!=null,"media reattach reestablishes observer");
  }
  // AOD transparent toggle & synchronized darkAmount transition tests
  ConfigManager.Config defCfg=new ConfigManager.Config();
  check(!defCfg.hideBgOnAod,"hideBgOnAod defaults to false");
  check(!ConfigManager.Config.fromJsonString("{\\\"hideBgOnAod\\\":false}").hideBgOnAod,"hideBgOnAod parses false");
  check(ConfigManager.Config.fromJsonString("{\\\"hideBgOnAod\\\":true}").hideBgOnAod,"hideBgOnAod parses true");
  check(ConfigManager.scaleAlpha(0xff1477ff,0.5f)==0x801477ff,"scaleAlpha scales 50% cleanly");
  check(ConfigManager.scaleAlpha(0xff1477ff,0.0f)==0x001477ff,"scaleAlpha zeros alpha at 0");
  for(int mode=0;mode<4;mode++) {
   boolean isBlur=(mode%2==1);
   boolean isGrad=(mode>=2);
   ConfigManager.Config aodCfg=new ConfigManager.Config();
   aodCfg.styleMode=isBlur?ConfigManager.STYLE_BLUR:ConfigManager.STYLE_SOLID;
   aodCfg.colorMode=isGrad?ConfigManager.MODE_GRADIENT:ConfigManager.MODE_FIXED_COLOR;
   aodCfg.hideBgOnAod=false;
   config.set(null,aodCfg);
   com.samsung.android.app.facewidget.presentation.NowBarMediaCardView cardAod=
    new com.samsung.android.app.facewidget.presentation.NowBarMediaCardView();
   Object uAod=constructor.newInstance(cardAod);
   cardAod.E=1.0f; cardAod.C=true;
   request.invoke(uAod); cardAod.getViewTreeObserver().draw();
   check(cardAod.p1.getBackground()!=null && cardAod.P.getAlpha()==1.0f && cardAod.p1.getElevation()==6f,"hideBgOnAod=false preserves AOD bg/stroke/elevation, mode="+mode);

   aodCfg.hideBgOnAod=true;
   cardAod.E=0.0f; cardAod.C=false;
   request.invoke(uAod); cardAod.getViewTreeObserver().draw();
   check(cardAod.p1.getBackground()!=null && cardAod.P.getAlpha()==1.0f && cardAod.p1.getElevation()==6f,"lockscreen E=0 shows full bg/stroke/elevation, mode="+mode);
   if(isBlur) check(cardAod.p1.inspectBlur()!=null && cardAod.p1.getBackground().getAlpha()==255,"lockscreen blur at 255 alpha, mode="+mode);

   cardAod.E=0.5f; cardAod.C=true;
   cardAod.getViewTreeObserver().draw();
   check(Math.abs(cardAod.P.getAlpha()-0.5f)<0.02f,"50% darkAmount fades border stroke alpha harmoniously, mode="+mode);
   check(Math.abs(cardAod.p1.getElevation()-3.0f)<0.05f,"50% darkAmount scales elevation harmoniously, mode="+mode);
   if(isBlur) {
    check(cardAod.p1.inspectBlur()!=null && Math.abs(cardAod.p1.getBackground().getAlpha()-128)<=1,"50% darkAmount fades blur drawable alpha harmoniously, mode="+mode);
   }

   cardAod.E=1.0f;
   cardAod.getViewTreeObserver().draw();
   check(cardAod.p1.getBackground()==null && cardAod.p1.inspectBlur()==null && cardAod.p1.getForeground()==null,"full AOD clears fill, blur, and foreground completely, mode="+mode);
   check(cardAod.P.getAlpha()==0.0f && cardAod.p1.getElevation()==0.0f,"full AOD hides border stroke and zeroes elevation, mode="+mode);

   cardAod.C=false; cardAod.E=0.5f;
   cardAod.getViewTreeObserver().draw();
   check(cardAod.p1.getBackground()!=null && Math.abs(cardAod.P.getAlpha()-0.5f)<0.02f,"wake 0.5 restores faded background and stroke, mode="+mode);
   if(isBlur) check(cardAod.p1.inspectBlur()!=null && Math.abs(cardAod.p1.getBackground().getAlpha()-128)<=1,"wake 0.5 restores blur at 50% alpha, mode="+mode);

   cardAod.E=0.0f;
   cardAod.getViewTreeObserver().draw();
   check(cardAod.p1.getBackground()!=null && cardAod.P.getAlpha()==1.0f && cardAod.p1.getElevation()==6f,"wake complete restores full bg/stroke/elevation, mode="+mode);
   if(isBlur) check(cardAod.p1.inspectBlur()!=null && cardAod.p1.getBackground().getAlpha()==255,"wake complete restores 255 blur alpha, mode="+mode);

   cardAod.M=false;
   cardAod.getViewTreeObserver().draw();
   check(cardAod.p1.getBackground()==null && cardAod.p1.inspectBlur()==null && cardAod.p1.getElevation()==0f,"ViewMode.AOD forces transparent pill, mode="+mode);

   cardAod.M=true; cardAod.E=1.0f;
   cardAod.getViewTreeObserver().draw();
   aodCfg.getCategory(ConfigManager.CAT_MEDIA).enabled=false;
   request.invoke(uAod); cardAod.getViewTreeObserver().draw();
   check(cardAod.P.getAlpha()==1.0f && cardAod.p1.getElevation()==6f,"disabled category restores border and elevation cleanly, mode="+mode);
  }
  config.set(null,disabled);
  Method part=NowBarHook.class.getDeclaredMethod("notificationPart",String.class,int.class);part.setAccessible(true);
  check(part.invoke(null,"0|com.clock|4660|flashlight|uid",3).equals("flashlight"),"notification tag");
  check(part.invoke(null,"malformed",2).equals(""),"malformed notification");
  check(part.invoke(null,"0|pkg||",3).equals(""),"empty notification tag");
  android.os.Handler main=new android.os.Handler(android.os.Looper.MAIN);
  hookField("sMainHandler").set(null,main);
  hookField("sSysUiContext").set(null,new android.content.Context());
  hookField("sLivePalette").set(null,new WallpaperColorHelper.PaletteResult());
  hookField("sConfigVersion").setInt(null,1);
  Runnable reload=(Runnable)hookField("RELOAD").get(null);
  reload.run(); android.os.Handler io=(android.os.Handler)hookField("sIoHandler").get(null);
  reload.run(); reload.run();
  check(io.pending()==1,"IO queue bounded while read in flight");
  io.one();
  ConfigManager.Config newer=new ConfigManager.Config();newer.masterEnabled=true;
  config.set(null,newer);hookField("sConfigVersion").setInt(null,2);
  main.one();check(config.get(null)==newer,"stale read cannot overwrite newer broadcast");
  main.one();main.one();
  check(io.pending()==1,"one follow-up for accumulated reads");
  io.one();main.one();
  check(!((ConfigManager.Config)config.get(null)).masterEnabled,"latest config read applied");
  main.one();check(main.pending()==0 && io.pending()==0,"IO queue drains without polling");
  // HookMap serialization, version matching, and strict missing/ambiguous null-safety
  HookMap hm = new HookMap(HookMap.SCHEMA_VERSION, 896548000L, "8.9.65.48", 123456789L,
    "com.samsung.android.app.facewidget.presentation.a", "U", "N", "q",
    "com.samsung.android.app.facewidget.utils.NowBarCardStackView", "q",
    "E", "h", "O", "D", "S", "T", "b0", "M", "M", "C", "l0",
    "com.samsung.android.app.facewidget.presentation.NowBarMediaCardView", "m0", "i0",
    "com.samsung.android.app.facewidget.presentation.NowBarBriefCardView", "h0", "e0",
    "com.samsung.android.app.facewidget.presentation.NowBarCardGradientView", "b",
    "com.samsung.android.app.aodservice.common.utils.settings.SettingsHelper", "A", null);
  HookMap roundTrip = HookMap.fromJson(hm.toJson());
  check(roundTrip != null && roundTrip.hasCoreHooks(), "HookMap roundtrip preserves core hooks");
  check(roundTrip.matchesTarget(896548000L, 123456789L), "HookMap matches versionCode + lastUpdateTime");
  check(!roundTrip.matchesTarget(896548001L, 123456789L), "HookMap rejects mismatched versionCode");
  check(!roundTrip.matchesTarget(896548000L, 999L), "HookMap rejects mismatched lastUpdateTime");
  check(roundTrip.totalSlotCount() == 27 && roundTrip.countResolvedSlots() == 27, "HookMap counts all 27 resolved slots");
  check("E".equals(roundTrip.baseShapeMethod) && "b0".equals(roundTrip.baseClearContainerBgMethod)
    && "S".equals(roundTrip.baseSetExpandStateMethod) && "l0".equals(roundTrip.baseExpandField)
    && "M".equals(roundTrip.baseLockscreenModeField) && "C".equals(roundTrip.baseDozingField)
    && "M".equals(roundTrip.baseAodDrawableMethod) && "i0".equals(roundTrip.mediaBlackAodMethod), "HookMap preserves wake, AOD field & drawable hooks");
  HookMap legacyV5 = new HookMap(5, 896548000L, "8.9.65.48", 123456789L,
    "cls.a", "U", "N", "q", "cls.s", "q",
    "E", "h", "O", "D", "S", "T", "b0", "M",
    "cls.m", "m0", "i0", "cls.b", "h0", "e0", "cls.g", "b", "cls.sh", "A", null);
  check(!legacyV5.matchesTarget(896548000L, 123456789L) && legacyV5.baseLockscreenModeField == null && legacyV5.baseDozingField == null, "Legacy schema v5 map triggers rescan for v6 slots");

  // AOD black GradientDrawable clearing, CardView m96 alpha hiding/restoration, and leak inspection
  NativeCard aodBlackCard = new NativeCard();
  CapsuleStyle.solid(aodBlackCard, 0xff224466, 42f);
  GradientDrawable samsungBlackAod = new GradientDrawable();
  samsungBlackAod.setColor(0xff000000);
  samsungBlackAod.setCornerRadius(42f);
  aodBlackCard.setBackground(samsungBlackAod);
  CapsuleStyle.transparent(aodBlackCard);
  check(aodBlackCard.getBackground() == null && aodBlackCard.color == 0, "transparent clears Samsung black GradientDrawable completely");

  NativeCard roundRectCard = new NativeCard();
  Drawable m96 = new Drawable();
  roundRectCard.setBackground(m96);
  roundRectCard.color = 0; // stale transparent ColorStateList
  int setsBefore = roundRectCard.sets;
  CapsuleStyle.transparent(roundRectCard);
  check(roundRectCard.sets > setsBefore && m96.getAlpha() == 0, "transparent forces setCardBackgroundColor(0) and zeroes RoundRectDrawable alpha");
  CapsuleStyle.solid(roundRectCard, 0xff1477ff, 42f);
  check(m96.getAlpha() == 255 && roundRectCard.color == 0 && roundRectCard.getBackground() instanceof GradientDrawable && ((GradientDrawable) roundRectCard.getBackground()).getColor().getDefaultColor() == 0xff1477ff, "wake solid restores RoundRectDrawable alpha and applies GradientDrawable cleanly");
  CapsuleStyle.transparent(roundRectCard);
  check(m96.getAlpha() == 0, "re-entering AOD zeroes RoundRectDrawable alpha again");
  CapsuleStyle.glass(roundRectCard, 0xff1477ff, 0xff1477ff, 42f);
  check(m96.getAlpha() == 255, "wake glass restores hidden alpha cleanly");

  Method inspectLeak = NowBarHook.class.getDeclaredMethod("inspectAodSurfaceLeak", View.class);
  inspectLeak.setAccessible(true);
  ConfigManager.Config aodTestCfg = new ConfigManager.Config();
  aodTestCfg.hideBgOnAod = true;
  config.set(null, aodTestCfg);
  com.samsung.android.app.facewidget.presentation.NowBarMediaCardView leakCard =
    new com.samsung.android.app.facewidget.presentation.NowBarMediaCardView();
  leakCard.E = 1.0f; leakCard.C = true;
  GradientDrawable leakedGd = new GradientDrawable();
  leakedGd.setColor(0xff000000);
  leakCard.setBackground(leakedGd);
  String detectedLeak = (String) inspectLeak.invoke(null, leakCard);
  check(detectedLeak.startsWith("AOD_BG_LEAK:") && detectedLeak.contains("ff000000"), "inspectAodSurfaceLeak flags residual black GradientDrawable with hex color");
  Object leakUpdate = constructor.newInstance(leakCard);
  request.invoke(leakUpdate);
  leakCard.getViewTreeObserver().draw();
  check("clean".equals(inspectLeak.invoke(null, leakCard)), "applyOrRestoreCardBackground clears root AOD black drawable to clean");

  // Media expand/collapse backdrop protection (o1 CardView + in-place m96 mutation + synchronous p1 repaint + expanded tree diagnostics)
  ConfigManager.Config expandCfg = new ConfigManager.Config();
  expandCfg.styleMode = ConfigManager.STYLE_SOLID;
  expandCfg.colorMode = ConfigManager.MODE_FIXED_COLOR;
  expandCfg.globalColor = 0xff1477ff;
  config.set(null, expandCfg);
  int expectedMediaArgb = expandCfg.resolveFinalArgbForCategory(ConfigManager.CAT_MEDIA, null);
  com.samsung.android.app.facewidget.presentation.NowBarMediaCardView expandTestCard =
    new com.samsung.android.app.facewidget.presentation.NowBarMediaCardView();
  NativeCard p1Card = new NativeCard();
  p1Card.setBackground(new Drawable());
  NativeCard o1Card = new NativeCard();
  Drawable o1m96 = new Drawable();
  o1Card.setBackground(o1m96);
  o1Card.setCardBackgroundColor(0xff000000);
  expandTestCard.p1 = p1Card;
  expandTestCard.o1 = o1Card;
  expandTestCard.registerChild("stack_background_cardview", p1Card);
  expandTestCard.registerChild("nowbar_cardview_background", o1Card);
  Object expandUpdate = constructor.newInstance(expandTestCard);
  request.invoke(expandUpdate);
  expandTestCard.getViewTreeObserver().draw();
  check(o1Card.color == 0 && o1Card.getElevation() == 0f
    && p1Card.getBackground() instanceof GradientDrawable
    && ((GradientDrawable) p1Card.getBackground()).getColor().getDefaultColor() == expectedMediaArgb
    && p1Card.getBackground().getAlpha() == 255
    && p1Card.color == 0, "collapsed media clears o1 black fill/elevation and colors p1");

  Method isSettingMode = NowBarHook.class.getDeclaredMethod("isCardInSettingMode", View.class);
  isSettingMode.setAccessible(true);
  expandTestCard.l0 = true;
  expandTestCard.j0 = 1;
  XposedHelpers.setAdditionalInstanceField(expandTestCard, "nb_expanded", Boolean.FALSE);
  expandTestCard.getViewTreeObserver().draw();
  check(!Boolean.TRUE.equals(isSettingMode.invoke(null, expandTestCard)), "expanded cardState j0=1 is not misclassified as setting mode");
  check(o1Card.color == 0 && o1Card.getElevation() == 0f && expandTestCard.v1.getAlpha() == 0f && expandTestCard.w1.getAlpha() == 0f, "native l0=true and j0=1 keep o1 transparent and shader hidden");
  String expandedDump = (String) hookField("sLastExpandedTreeDump").get(null);
  check(expandedDump != null && expandedDump.contains("NowBarMediaCardView"), "expanded media transition records sLastExpandedTreeDump");

  o1Card.setCardBackgroundColor(0xff000000);
  o1Card.setElevation(6f);
  expandTestCard.getViewTreeObserver().draw();
  check(o1Card.color == 0 && o1Card.getElevation() == 0f && o1Card.getBackground() == o1m96, "MediaLayer detects in-place m96 CardView mutation on o1 while expanded (j0=1) and clears it");

  int stableO1Sets = o1Card.sets;
  for (int f = 0; f < 20; f++) expandTestCard.getViewTreeObserver().draw();
  check(o1Card.sets == stableO1Sets, "expanded transparent o1 incurs zero idle restyles");

  Method handleExpand = NowBarHook.class.getDeclaredMethod("handleExpandTransition", View.class, Boolean.class, boolean.class);
  handleExpand.setAccessible(true);
  expandTestCard.l0 = false;
  expandTestCard.j0 = 0;
  p1Card.setCardBackgroundColor(0xff000000);
  p1Card.getBackground().setAlpha(0);
  handleExpand.invoke(null, expandTestCard, Boolean.FALSE, Boolean.TRUE);
  check(p1Card.getBackground() instanceof GradientDrawable
    && ((GradientDrawable) p1Card.getBackground()).getColor().getDefaultColor() == expectedMediaArgb
    && p1Card.getBackground().getAlpha() == 255
    && p1Card.color == 0 && o1Card.color == 0, "beforeCall collapse transition repaints p1 and restores alpha synchronously");

  expandTestCard.j0 = 2;
  check(Boolean.TRUE.equals(isSettingMode.invoke(null, expandTestCard)), "actual setting cardState j0=2 still triggers setting mode");

  // Regression: hideBgOnAod=true -> AOD -> Wake -> Expand keeps t1, o1, p1 free of black leaks
  NativeCard t1Card = new NativeCard();
  GradientDrawable t1Fg = new GradientDrawable();
  t1Fg.setColor(0xff000000);
  t1Card.setForeground(t1Fg);
  t1Card.setCardBackgroundColor(0xff2b2b2b);
  expandTestCard.t1 = t1Card;
  expandTestCard.registerChild("nowbar_cardview_bgview_for_media_setting", t1Card);
  expandTestCard.j0 = 0;
  expandTestCard.l0 = false;
  expandCfg.hideBgOnAod = true;
  expandTestCard.E = 1.0f;
  expandTestCard.C = true;
  expandTestCard.getViewTreeObserver().draw();
  check(t1Card.getForeground() == null && t1Card.color == 0 && o1Card.color == 0, "hideBgOnAod=true in AOD clears t1 and o1 black fills");

  expandTestCard.E = 0.0f;
  expandTestCard.C = false;
  expandTestCard.getViewTreeObserver().draw();
  expandTestCard.l0 = true;
  expandTestCard.j0 = 1;
  expandTestCard.getViewTreeObserver().draw();
  check(o1Card.color == 0 && t1Card.color == 0 && t1Card.getForeground() == null && p1Card.color == 0 && !(p1Card.getBackground() instanceof GradientDrawable), "wake -> expand with hideBgOnAod=true keeps o1, t1, and p1 transparent");

  Method inspectExpLeak = NowBarHook.class.getDeclaredMethod("inspectMediaExpandedLeak", View.class);
  inspectExpLeak.setAccessible(true);
  check("clean".equals(inspectExpLeak.invoke(null, expandTestCard)), "inspectMediaExpandedLeak reports clean when backdrops are cleared");
  t1Card.setForeground(t1Fg);
  String expLeakDetected = (String) inspectExpLeak.invoke(null, expandTestCard);
  check(expLeakDetected.startsWith("MEDIA_BG_LEAK:"), "inspectMediaExpandedLeak detects opaque t1 foreground leak");
  Method applyBackdropClear = NowBarHook.class.getDeclaredMethod("applyExpandedMediaBackdropClear", View.class);
  applyBackdropClear.setAccessible(true);
  applyBackdropClear.invoke(null, expandTestCard);
  check("clean".equals(inspectExpLeak.invoke(null, expandTestCard)), "applyExpandedMediaBackdropClear removes t1 leak cleanly");

  // Setter-tracked AOD state wins over stale/renamed R8 M/C fields across AOD_Service branches
  expandTestCard.l0 = false;
  expandTestCard.j0 = 0;
  expandTestCard.M = false;
  expandTestCard.C = true;
  XposedHelpers.setAdditionalInstanceField(expandTestCard, "nb_lockscreen_mode", Boolean.TRUE);
  XposedHelpers.setAdditionalInstanceField(expandTestCard, "nb_dozing", Boolean.FALSE);
  Method aodProg = NowBarHook.class.getDeclaredMethod("getCardAodProgress", View.class);
  aodProg.setAccessible(true);
  check(((Float) aodProg.invoke(null, expandTestCard)) == 0.0f, "tracked setLockscreenViewMode/setDozing values override stale M/C fields");

  config.set(null, disabled);

  java.util.LinkedHashMap<String, String> st = new java.util.LinkedHashMap<>();
  st.put("baseCardClass", "OK");
  st.put("baseBindMethod", "AMBIGUOUS(2: m1, m2)");
  st.put("baseBlurMethod", "MISSING(0)");
  HookMap partial = new HookMap(HookMap.SCHEMA_VERSION, 896548000L, "8.9.65.48", 123L,
    "cls.a", null, null, "q", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, st);
  HookMap partialRt = HookMap.fromJson(partial.toJson());
  check(partialRt != null && !partialRt.hasCoreHooks() && partialRt.hasAnyCoreHooks(), "HookMap allows partial core hooks while requiring all core methods for full compatibility");
  check(partialRt.countResolvedSlots() == 2, "HookMap counts partial slots accurately");
  check("AMBIGUOUS(2: m1, m2)".equals(partialRt.getSlotStatus("baseBindMethod", partialRt.baseBindMethod)), "HookMap preserves AMBIGUOUS status");
  check("MISSING(0)".equals(partialRt.getSlotStatus("baseBlurMethod", partialRt.baseBlurMethod)), "HookMap preserves MISSING(0) status");
  check(partialRt.formatDiagnosticsLines().contains("AMBIGUOUS(2: m1, m2)"), "Diagnostics report includes AMBIGUOUS candidates");

  // --- One UI 8.5 (8.9.32.89 / Android 16) Isolated Adapter & R8 Collision Tests ---
  java.util.LinkedHashMap<String, String> st85 = new java.util.LinkedHashMap<>();
  st85.put("baseStateMethod", "OK");
  st85.put("showNotiLockscreenMethod", "OK");
  st85.put("baseClearContainerBgMethod", "N/A (OneUI <9)");
  HookMap hm85 = new HookMap(HookMap.SCHEMA_VERSION, 893289000L, "8.9.32.89", 777L,
    "com.samsung.android.app.facewidget.presentation.a", "Q", "K", "n",
    "com.samsung.android.app.facewidget.utils.NowBarCardStackView", "q",
    "B", "f", "L", "A", "O", "P", null, "J", "D", "u", "l0",
    "com.samsung.android.app.facewidget.presentation.NowBarMediaCardView", "i0", "e0",
    "com.samsung.android.app.facewidget.presentation.NowBarBriefCardView", "c0", "a0",
    "com.samsung.android.app.facewidget.presentation.NowBarCardGradientView", "b",
    "com.samsung.android.app.aodservice.common.utils.settings.SettingsHelper", "H", st85);
  HookMap hm85Rt = HookMap.fromJson(hm85.toJson());
  check(hm85Rt != null && hm85Rt.hasCoreHooks(), "OneUI 8.5 HookMap roundtrip preserves core hooks");
  check(hm85Rt.isSlotNotApplicable("baseClearContainerBgMethod"), "OneUI 8.5 marks baseClearContainerBgMethod N/A");
  check(hm85Rt.totalSlotCount() == 26 && hm85Rt.countResolvedSlots() == 26, "OneUI 8.5 HookMap counts 26/26 applicable slots");
  check(hm85Rt.formatDiagnosticsLines().contains("N/A (OneUI <9)"), "OneUI 8.5 diagnostics prints N/A cleanly");

  check(NowBarAdapterFactory.resolve(null, hm85Rt) == OneUi85Adapter.INSTANCE, "Factory selects OneUi85Adapter for 893289000");
  check(NowBarAdapterFactory.resolve(null, roundTrip) == OneUi9Adapter.INSTANCE, "Factory selects OneUi9Adapter for 896548000");
  HookMap oneUi9Minimal = new HookMap(HookMap.SCHEMA_VERSION, 896000000L, "8.9.60.0", 1L,
    "a", "U", "N", "q", "s", "q", "E", "h", "O", "D", "S", "T", "b0", "M", "M", "C",
    "m", "m0", "i0", "br", "h0", "e0", "g", "b", "sh", "A", null);
  check(NowBarAdapterFactory.resolve(null, oneUi9Minimal) == OneUi9Adapter.INSTANCE, "Factory selects OneUi9Adapter when baseClearContainerBgMethod present");

  Field adapterField = hookField("sAdapter");
  Field hookMapField = hookField("sHookMap");
  Object prevAdapter = adapterField.get(null);
  Object prevHookMap = hookMapField.get(null);
  try {
   ConfigManager.Config solid85 = new ConfigManager.Config();
   solid85.masterEnabled = true;
   solid85.styleMode = ConfigManager.STYLE_SOLID;
   solid85.hideBgOnAod = false;
   solid85.getCategory(ConfigManager.CAT_OTHER_ONGOING).enabled = true;
   solid85.getCategory(ConfigManager.CAT_BRIEF).enabled = true;
   adapterField.set(null, OneUi85Adapter.INSTANCE);
   hookMapField.set(null, hm85Rt);
   config.set(null, solid85);

   com.samsung.android.app.facewidget.presentation.NowBarOngoingCardView ongoing85 =
     new com.samsung.android.app.facewidget.presentation.NowBarOngoingCardView();
   ongoing85.G.setAlpha(1.0f);
   ongoing85.F.setAlpha(1.0f);
   ongoing85.K.setAlpha(1.0f);
   ongoing85.nudge.setAlpha(1.0f);
   ongoing85.l0.setAlpha(1.0f);

   Method applyCard = NowBarHook.class.getDeclaredMethod("applyOrRestoreCardBackground", View.class);
   applyCard.setAccessible(true);
   applyCard.invoke(null, ongoing85);

   check(ongoing85.G.getAlpha() == 1.0f, "OneUi85Adapter MUST NOT clobber G (normal_content_rootview alpha=0 bug fixed!)");
   check(ongoing85.F.getAlpha() == 1.0f, "OneUi85Adapter MUST NOT clobber F (border stroke)");
   check(ongoing85.K.getBackground() == null && ongoing85.K.getAlpha() == 1.0f, "OneUi85Adapter MUST NOT touch K (NowbarCardSettingView)");
   check(ongoing85.B.getBackground() instanceof GradientDrawable && ((GradientDrawable) ongoing85.B.getBackground()).getCornerRadius() == 48f, "OneUi85Adapter resolves B via getCardViewContainer and radius via getNormalCardRadius");
   check(ongoing85.nudge.getAlpha() == 0.0f && ongoing85.l0.getAlpha() == 0.0f && ongoing85.l0.getVisibility() == View.GONE, "OneUi85Adapter suppresses real nudge_container and gradient_view");

   config.set(null, disabled);
   applyCard.invoke(null, ongoing85);
   check(ongoing85.bCalls == 1 && ongoing85.eCalls == 0, "OneUi85Adapter restore triggers HookMap baseShapeMethod B(), not hardcoded E()");

   config.set(null, solid85);
   com.samsung.android.app.facewidget.presentation.NowBarBriefCardView brief85 =
     new com.samsung.android.app.facewidget.presentation.NowBarBriefCardView();
   brief85.G.setAlpha(1.0f);
   applyCard.invoke(null, brief85);
   check(brief85.G.getAlpha() == 1.0f, "OneUi85Adapter keeps NowBarBriefCardView normal_content_rootview visible");
   check(brief85.briefBg.getBackground() != null, "OneUi85Adapter styles nowbar_cardview_background_for_now_brief");

   config.set(null, disabled);
   applyCard.invoke(null, brief85);
   check(brief85.fCalls == 1 && Math.abs(brief85.lastDark - 0.25f) < 0.001f, "OneUi85Adapter brief restore calls baseProgressBgMethod f(darkAmount)");
   check(brief85.l0Calls == 0 && brief85.eCalls == 0 && brief85.bCalls == 1, "OneUi85Adapter brief restore calls B() cleanly without l0/E");

   // Recalibrated Color Blur (16-100px radius, 25-85% tint opacity), specular border & adaptive contrast
   ConfigManager.Config blurCfg = new ConfigManager.Config();
   check(blurCfg.opacity == 92 && blurCfg.blurIntensity == 60 && blurCfg.blurTintOpacity == 60, "defaults keep Solid opacity=92 and Color Blur at 60%/60%");
   check(OneUiBlurSpec.radiusPxFromPercent(0) == 16 && OneUiBlurSpec.radiusPxFromPercent(60) == 66 && OneUiBlurSpec.radiusPxFromPercent(100) == 100, "blurIntensity maps cleanly onto 16px..100px");
   check(Math.abs(OneUiBlurSpec.previewBlurRadiusPx(66) - 27.72f) < 0.05f, "preview blur scales proportionally without destroying backdrop shapes");
   check(OneUiBlurSpec.tintAlphaFromPercent(60) == 153 && OneUiBlurSpec.clampTintOpacity(5) == 25 && OneUiBlurSpec.clampTintOpacity(99) == 85, "tint opacity clamps to 25%-85% and yields 60% (~0x99)");
   ConfigManager.Config parsedBlur = ConfigManager.Config.fromJsonString(
     "{\\\"opacity\\\":92,\\\"blurIntensity\\\":45,\\\"blurTintOpacity\\\":95}");
   check(parsedBlur.opacity == 92 && parsedBlur.blurIntensity == 45 && parsedBlur.blurTintOpacity == 85, "JSON preserves Solid opacity independently and clamps blurTintOpacity");

   blurCfg.masterEnabled = true;
   blurCfg.styleMode = ConfigManager.STYLE_BLUR;
   blurCfg.colorMode = ConfigManager.MODE_GRADIENT;
   blurCfg.gradientStartColor = 0xFFFFD600; // bright yellow
   blurCfg.gradientEndColor = 0xFF7B1FA2;   // deep purple
   blurCfg.blurIntensity = 60;
   blurCfg.blurTintOpacity = 62;
   blurCfg.getCategory(ConfigManager.CAT_OTHER_ONGOING).enabled = true;
   Drawable origBorder = new Drawable();
   ongoing85.F.setBackground(origBorder);
   android.widget.TextView titleTv = new android.widget.TextView();
   ongoing85.G.addView(titleTv);
   config.set(null, blurCfg);
   applyCard.invoke(null, ongoing85);

   check(ongoing85.B.inspectBlur() != null && ongoing85.B.inspectBlur().radius == 66 && ongoing85.B.inspectBlur().sat == 0.38f, "OneUi85 Color Blur applies 66px radius and +0.38 saturation");
   checkForegroundGradient(ongoing85.B, OneUiBlurSpec.applyTintOpacity(0xFFFFD600, 62), OneUiBlurSpec.applyTintOpacity(0xFF7B1FA2, 62), "yellow-to-purple blur tint preserves vivid 62% foreground gradient");
   check(ongoing85.F.getBackground() != null && ongoing85.F.getBackground() != origBorder, "Blur mode installs diagonal specular border on nowbar_card_border_stroke_view");
   check(titleTv.getTextColors().getDefaultColor() == 0xFFFFFFFF && titleTv.lastShadowRadius > 0f && titleTv.lastShadowColor == OneUiBlurSpec.CONTRAST_SHADOW_COLOR, "yellow-to-purple gradient keeps white text with contrast drop shadow");

   blurCfg.colorMode = ConfigManager.MODE_FIXED_COLOR;
   blurCfg.globalColor = 0xFFFFEB3B; // uniform bright yellow
   blurCfg.blurTintOpacity = 80;
   applyCard.invoke(null, ongoing85);
   check(titleTv.getTextColors().getDefaultColor() == OneUiBlurSpec.DARK_CONTENT_COLOR && titleTv.lastShadowRadius == 0f, "uniform bright yellow switches compatible text to high-contrast charcoal");

   blurCfg.styleMode = ConfigManager.STYLE_SOLID;
   blurCfg.globalColor = 0xFF1E88E5;
   applyCard.invoke(null, ongoing85);
   check(ongoing85.F.getBackground() == origBorder, "switching to Solid restores original Samsung border drawable");
   check(titleTv.getTextColors().getDefaultColor() == 0xFFFFFFFF && titleTv.lastShadowRadius == 0f, "dark solid color restores original white text cleanly");

   // --- One UI 8.0 (8.9.15.39 / SM-S901E) Isolated Adapter, Expand, 5s AOD Wake & Leak Tests ---
   java.util.LinkedHashMap<String, String> st80 = new java.util.LinkedHashMap<>();
   st80.put("baseProgressBgMethod", "N/A (OneUI <8.5)");
   st80.put("baseClearContainerBgMethod", "N/A (OneUI <9)");
   st80.put("mediaForegroundMethod", "N/A (OneUI <8.5)");
   st80.put("briefCardClass", "N/A (OneUI <8.5)");
   st80.put("briefBgColorMethod", "N/A (OneUI <8.5)");
   st80.put("briefResetBgMethod", "N/A (OneUI <8.5)");
   HookMap hm80 = new HookMap(HookMap.SCHEMA_VERSION, 891539000L, "8.9.15.39", 808L,
     "com.samsung.android.app.facewidget.presentation.a", "M", "G", "l",
     "com.samsung.android.app.facewidget.utils.NowBarCardStackView", "m",
     "A", null, "H", "z", "K", "L", null, "F", "z", "r", "Q",
     "com.samsung.android.app.facewidget.presentation.NowBarMediaCardView", null, "U",
     null, null, null,
     "com.samsung.android.app.facewidget.presentation.NowBarCardGradientView", "b",
     "com.samsung.android.app.aodservice.common.utils.settings.SettingsHelper", "D", st80);
   HookMap hm80Rt = HookMap.fromJson(hm80.toJson());
   check(hm80Rt != null && hm80Rt.hasCoreHooks(), "OneUI 8.0 HookMap preserves full Core with CardView baseBlurMethod G");
   check(hm80Rt.totalSlotCount() == 21 && hm80Rt.countResolvedSlots() == 21, "OneUI 8.0 HookMap counts 21/21 applicable slots");
   check("Q".equals(hm80Rt.baseExpandField) && "r".equals(hm80Rt.baseDozingField) && "z".equals(hm80Rt.baseLockscreenModeField), "OneUI 8.0 preserves Q/r/z fields cleanly");
   check(NowBarAdapterFactory.resolve(null, hm80Rt) == OneUi80Adapter.INSTANCE, "Factory selects OneUi80Adapter for 891539000");

   com.samsung.android.app.facewidget.presentation.NowBarExternalViewCardView ext80 =
     new com.samsung.android.app.facewidget.presentation.NowBarExternalViewCardView();
   check(NowBarAdapterFactory.resolve(ext80.getClass(), null) == OneUi80Adapter.INSTANCE, "Factory resolves OneUi80Adapter from 8.0 capabilities");

   adapterField.set(null, OneUi80Adapter.INSTANCE);
   hookMapField.set(null, hm80Rt);

   Method readNativeExp = NowBarHook.class.getDeclaredMethod("readNativeExpandedState", View.class);
   readNativeExp.setAccessible(true);
   check(Boolean.FALSE.equals(readNativeExp.invoke(null, ext80)), "OneUI 8.0 reads boolean Q field instead of Kotlin Lazy l0 Object");
   ext80.Q = true;
   check(Boolean.TRUE.equals(readNativeExp.invoke(null, ext80)), "OneUI 8.0 reflects Q=true expand state");
   ext80.Q = false;

   ConfigManager.Config aod80 = new ConfigManager.Config();
   aod80.masterEnabled = true;
   aod80.hideBgOnAod = true;
   aod80.getCategory(ConfigManager.CAT_BRIEF).enabled = true;
   config.set(null, aod80);
   XposedHelpers.setAdditionalInstanceField(ext80, "nb_dozing", Boolean.TRUE);
   ext80.z = true;
   ext80.r = false;
   ext80.t = 0.0f;
   check(((Float) aodProg.invoke(null, ext80)) == 0.0f, "OneUI 8.0 live baseDozingField r=false overrides stale nb_dozing=true after >5s wake");
   ext80.r = true;
   XposedHelpers.setAdditionalInstanceField(ext80, "nb_dozing", Boolean.FALSE);
   XposedHelpers.setAdditionalInstanceField(ext80, "nb_doze_zero_since", System.nanoTime() - 300_000_000L);
   check(((Float) aodProg.invoke(null, ext80)) == 1.0f, "OneUI 8.0 live baseDozingField r=true enters AOD even when colorStateLiveData mutated r directly");
   ext80.r = false;
   check(((Float) aodProg.invoke(null, ext80)) == 0.0f, "OneUI 8.0 wakes immediately back to vis=1 when r becomes false");

   blurCfg.styleMode = ConfigManager.STYLE_BLUR;
   blurCfg.colorMode = ConfigManager.MODE_GRADIENT;
   blurCfg.gradientStartColor = 0xFFFFD600;
   blurCfg.gradientEndColor = 0xFF7B1FA2;
   blurCfg.blurIntensity = 100;
   blurCfg.blurTintOpacity = 85;
   blurCfg.getCategory(ConfigManager.CAT_BRIEF).enabled = true;
   config.set(null, blurCfg);
   ext80.Q = false;
   handleExpand.invoke(null, ext80, Boolean.FALSE, Boolean.FALSE);
   ext80.getViewTreeObserver().draw();
   check(ext80.u.getAlpha() == 0.0f && ext80.v.getAlpha() == 0.0f && ext80.f0.getAlpha() == 0.0f, "OneUI 8.0 AI_BRIEF card suppresses guidingBg u, guidingEffect v, and gradient f0");
   checkForegroundGradient(ext80.x, OneUiBlurSpec.applyTintOpacity(0xFFFFD600, 85), OneUiBlurSpec.applyTintOpacity(0xFF7B1FA2, 85), "OneUI 8.0 applies custom glass style on CardView x");
   ext80.x.semSetBlurInfo(new SemBlurInfo());
   ext80.getViewTreeObserver().draw();
   check(!getter || (ext80.x.inspectBlur() != null && ext80.x.inspectBlur().radius == 100), "MediaLayer detects native SemBlurInfo overwrite and restores custom blur on pre-draw");

   config.set(null, disabled);
   applyCard.invoke(null, ext80);
   check(ext80.aCalls == 1 && ext80.u.getAlpha() == 1.0f && ext80.v.getAlpha() == 1.0f, "OneUi80Adapter restore triggers shape refresh A() and restores u/v cleanly");

   // Action button MaterialCardView (ongoing_sec_action_bg) must not trigger false AOD_BG_LEAK
   Drawable actionFg = new Drawable();
   actionFg.setAlpha(255);
   ongoing85.actionBg.setForeground(actionFg);
   CapsuleStyle.transparent(ongoing85.B);
   check("clean".equals(inspectLeak.invoke(null, ongoing85)), "inspectAodSurfaceLeak ignores ongoing_sec_action_bg action button MaterialCardView");
   GradientDrawable realCardLeak = new GradientDrawable();
   realCardLeak.setColor(0xff000000);
   ongoing85.B.setBackground(realCardLeak);
   check(((String) inspectLeak.invoke(null, ongoing85)).startsWith("AOD_BG_LEAK:"), "inspectAodSurfaceLeak still catches opaque nowbar_cardview_background leak");

   // --- MODE_IMAGE (Custom Capsule Photos, Legibility Scrim, Contrast, Security & AOD Dim) ---
   ConfigManager.Config rawImg = new ConfigManager.Config();
   rawImg.colorMode = ConfigManager.MODE_IMAGE;
   rawImg.imageRev = 8888L;
   rawImg.imageStyle = 99;
   rawImg.imageBlurPercent = 250;
   rawImg.imageScrimPercent = 120;
   rawImg.imageScrimMode = 77;
   rawImg.imageDuotonePreset = -5;
   rawImg.imageDomColor = 0x11FF6600;
   rawImg.imageLuma = 310;
   ConfigManager.Config parsedImg = ConfigManager.Config.fromJsonString(rawImg.toJsonString());
   check(parsedImg.colorMode == ConfigManager.MODE_IMAGE && parsedImg.imageRev == 8888L, "MODE_IMAGE JSON roundtrip preserves mode and revision");
   check(parsedImg.imageStyle == ConfigManager.clampImageStyle(99)
     && parsedImg.imageBlurPercent == ConfigManager.clampImageBlurPercent(250)
     && parsedImg.imageScrimPercent == ConfigManager.clampImageScrimPercent(120)
     && parsedImg.imageScrimMode == ConfigManager.clampScrimMode(77)
     && parsedImg.imageDuotonePreset == ConfigManager.clampDuotonePreset(-5),
     "MODE_IMAGE JSON roundtrip clamps style, blur, scrim, mode, and duotone preset");
   check(parsedImg.imageScrimPercent == 80 && ConfigManager.clampImageScrimPercent(-5) == 0, "image scrim clamps cleanly to 0%-80%");
   check(parsedImg.imageDomColor == 0xFFFF6600 && parsedImg.imageLuma == 255, "MODE_IMAGE JSON preserves opaque dominant color and clamped luma");

   ConfigManager.Config unbakedImg = new ConfigManager.Config();
   unbakedImg.colorMode = ConfigManager.MODE_IMAGE;
   unbakedImg.imageRev = 0L;
   check(!unbakedImg.isImageActiveForCategory(ConfigManager.CAT_MEDIA) && unbakedImg.resolveImageSlotForCategory(ConfigManager.CAT_MEDIA) == null, "unbaked MODE_IMAGE (rev=0) stays inactive until image picked");
   check(parsedImg.isImageActiveForCategory(ConfigManager.CAT_MEDIA) && "global".equals(parsedImg.resolveImageSlotForCategory(ConfigManager.CAT_MEDIA)), "active global MODE_IMAGE resolves slot 'global'");

   ConfigManager.Config perCatImg = new ConfigManager.Config();
   perCatImg.colorMode = ConfigManager.MODE_FIXED_COLOR;
   ConfigManager.CategoryConfig mediaImgCc = perCatImg.getCategory(ConfigManager.CAT_MEDIA);
   mediaImgCc.useCustomColor = true;
   mediaImgCc.customColorMode = ConfigManager.MODE_IMAGE;
   mediaImgCc.customImageRev = 999L;
   mediaImgCc.customImageDomColor = 0xFF00AAFF;
   mediaImgCc.customImageLuma = 240;
   mediaImgCc.customImageScrimPercent = 80;
   mediaImgCc.customImageScrimMode = ConfigManager.SCRIM_UNIFORM;
   check(perCatImg.isImageActiveForCategory(ConfigManager.CAT_MEDIA) && "cat_media".equals(perCatImg.resolveImageSlotForCategory(ConfigManager.CAT_MEDIA)), "per-category MODE_IMAGE resolves cat_media slot");
   check(!perCatImg.isImageActiveForCategory(ConfigManager.CAT_OTHER_ONGOING) && perCatImg.resolveImageSlotForCategory(ConfigManager.CAT_OTHER_ONGOING) == null, "other categories stay unaffected when only Media overrides image");
   check(perCatImg.resolveBaseRgbForCategory(ConfigManager.CAT_MEDIA, null) == 0xFF00AAFF, "MODE_IMAGE resolves dominant color as category base RGB fallback");

   int noScrimLight = OneUiBlurSpec.computeImageContrastArgb(240, 0, ConfigManager.SCRIM_UNIFORM);
   check(noScrimLight == 0xFFF0F0F0 && OneUiBlurSpec.needsDarkForeground(noScrimLight, noScrimLight, false), "bright 0%-scrim photo triggers dark foreground contrast");
   int heavyScrimLight = OneUiBlurSpec.computeImageContrastArgb(240, 80, ConfigManager.SCRIM_UNIFORM);
   check(heavyScrimLight == 0xFF303030 && !OneUiBlurSpec.needsDarkForeground(heavyScrimLight, heavyScrimLight, false), "80% scrim darkens bright photo enough for crisp white foreground");
   int gradScrimContrast = OneUiBlurSpec.computeImageContrastArgb(200, 40, ConfigManager.SCRIM_GRADIENT);
   int uniScrimContrast = OneUiBlurSpec.computeImageContrastArgb(200, 40, ConfigManager.SCRIM_UNIFORM);
   check((gradScrimContrast & 0xFF) > (uniScrimContrast & 0xFF), "reading gradient computes lighter average darkening factor than uniform scrim");
   check(OneUiBlurSpec.blendRgb(0xFF000000, 0xFFFFFFFF, 0.5f) == 0xFF808080, "blendRgb mid-point blends RGB channels cleanly");
   check(OneUiBlurSpec.createTintedSpecularBorderShader(0f, 0f, 400f, 100f, 0xFFFF5500, 1.0f) != null, "createTintedSpecularBorderShader builds gradient cleanly");

   check("global".equals(CapsuleImageProcessor.sanitizeSlot("global")), "sanitizeSlot allows global");
   check("cat_media".equals(CapsuleImageProcessor.sanitizeSlot("  CAT_media ")), "sanitizeSlot normalizes valid category slot");
   check(CapsuleImageProcessor.sanitizeSlot("../secret") == null
     && CapsuleImageProcessor.sanitizeSlot("cat/a") == null
     && CapsuleImageProcessor.sanitizeSlot("cat_") == null
     && CapsuleImageProcessor.sanitizeSlot("") == null
     && CapsuleImageProcessor.sanitizeSlot(null) == null,
     "sanitizeSlot rejects path traversal and invalid slots");
   int[] cyberPair = CapsuleImageProcessor.resolveDuotoneColors(ConfigManager.DUOTONE_CYBER, 0, 0);
   check(cyberPair != null && cyberPair.length == 2 && cyberPair[0] == 0xFF160B33 && cyberPair[1] == 0xFF00E5FF, "resolveDuotoneColors returns Cyber shadow/highlight pair");
   int[] monetPair = CapsuleImageProcessor.resolveDuotoneColors(ConfigManager.DUOTONE_MONET, 0xFF336699, 0xFFFFAA33);
   check(monetPair != null && monetPair.length == 2 && (monetPair[0] >>> 24) == 0xFF && (monetPair[1] >>> 24) == 0xFF && monetPair[0] != monetPair[1], "resolveDuotoneColors derives distinct opaque Monet pair");

   check("content://io.github.tavataan.nowbarcolor.provider/image/global".equals(NowBarConfigProvider.buildImageUri("global").toString()), "buildImageUri builds global URI");
   check("content://io.github.tavataan.nowbarcolor.provider/image/invalid".equals(NowBarConfigProvider.buildImageUri("../etc").toString()), "buildImageUri neutralizes unsafe slot");
   java.io.File testRoot = new java.io.File(HostTests.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParentFile();
   java.io.File tmpImgDir = java.nio.file.Files.createTempDirectory(testRoot.toPath(), "nb_img_host").toFile();
   tmpImgDir.deleteOnExit();
   android.content.Context imgProviderCtx = new android.content.Context();
   imgProviderCtx.setFilesDir(tmpImgDir);
   boolean badUriRejected = false;
   try { NowBarConfigProvider.resolveSafeImageFile(imgProviderCtx, android.net.Uri.parse("content://io.github.tavataan.nowbarcolor.provider/config")); }
   catch (java.io.FileNotFoundException expected) { badUriRejected = true; }
   check(badUriRejected, "resolveSafeImageFile rejects non-image URI");
   boolean traversalRejected = false;
   try { NowBarConfigProvider.resolveSafeImageFile(imgProviderCtx, android.net.Uri.parse("content://io.github.tavataan.nowbarcolor.provider/image/..")); }
   catch (java.io.FileNotFoundException expected) { traversalRejected = true; }
   check(traversalRejected, "resolveSafeImageFile rejects path traversal segment");
   java.io.File dummyBaked = CapsuleImageProcessor.getBakedImageFile(imgProviderCtx, "global");
   dummyBaked.getParentFile().mkdirs();
   try (java.io.FileOutputStream fos = new java.io.FileOutputStream(dummyBaked)) { fos.write(new byte[]{1, 2, 3, 4}); }
   dummyBaked.deleteOnExit();
   check(dummyBaked.getCanonicalPath().equals(NowBarConfigProvider.resolveSafeImageFile(imgProviderCtx, NowBarConfigProvider.buildImageUri("global")).getCanonicalPath()), "resolveSafeImageFile resolves valid baked image inside capsule_images");

   View imgView = new View();
   Drawable imgOrigBg = new Drawable();
   imgView.setBackground(imgOrigBg);
   CapsuleStyle.image(imgView, null, 0xFFCC4411, 48f, 40, ConfigManager.SCRIM_GRADIENT, 1.0f, true);
   check(imgView.getBackground() instanceof CapsuleImageDrawable && !CapsuleStyle.needsGradientRepair(imgView), "CapsuleStyle.image installs healthy CapsuleImageDrawable");
   imgView.getBackground().setAlpha(0);
   check(CapsuleStyle.needsGradientRepair(imgView), "CapsuleImageDrawable setAlpha(0) triggers needsGradientRepair");
   CapsuleStyle.image(imgView, null, 0xFFCC4411, 48f, 40, ConfigManager.SCRIM_GRADIENT, 1.0f, true);
   check(!CapsuleStyle.needsGradientRepair(imgView) && imgView.getBackground().getAlpha() == 255, "CapsuleStyle.image repairs alpha back to 255");
   imgView.getBackground().setTint(0);
   check(CapsuleStyle.needsGradientRepair(imgView), "CapsuleImageDrawable setTint(0) triggers needsGradientRepair");
   imgView.setBackgroundTintList(new ColorStateList(0x55000000));
   check(CapsuleStyle.needsGradientRepair(imgView), "native View backgroundTintList triggers needsGradientRepair");
   CapsuleStyle.image(imgView, null, 0xFFCC4411, 48f, 40, ConfigManager.SCRIM_GRADIENT, 1.0f, true);
   check(imgView.getBackgroundTintList() == null && !CapsuleStyle.needsGradientRepair(imgView), "CapsuleStyle.image clears View backgroundTintList");
   CapsuleStyle.restoreGradient(imgView);
   check(imgView.getBackground() == imgOrigBg, "restoreGradient restores original background after MODE_IMAGE");

   adapterField.set(null, OneUi85Adapter.INSTANCE);
   hookMapField.set(null, hm85Rt);
   ConfigManager.Config imgHookCfg = new ConfigManager.Config();
   imgHookCfg.masterEnabled = true;
   imgHookCfg.colorMode = ConfigManager.MODE_IMAGE;
   imgHookCfg.imageRev = 555L;
   imgHookCfg.imageDomColor = 0xFFFFEB3B;
   imgHookCfg.imageLuma = 240;
   imgHookCfg.imageScrimPercent = 0;
   imgHookCfg.imageScrimMode = ConfigManager.SCRIM_UNIFORM;
   imgHookCfg.hideBgOnAod = false;
   imgHookCfg.getCategory(ConfigManager.CAT_OTHER_ONGOING).enabled = true;
   imgHookCfg.getCategory(ConfigManager.CAT_MEDIA).enabled = true;
   config.set(null, imgHookCfg);

   ongoing85.B.setBackground(new Drawable());
   Drawable imgCardBorderOrig = new Drawable();
   ongoing85.F.setBackground(imgCardBorderOrig);
   ongoing85.w = 0.0f;
   XposedHelpers.setAdditionalInstanceField(ongoing85, "nb_dozing", Boolean.FALSE);
   XposedHelpers.setAdditionalInstanceField(ongoing85, "nb_lockscreen_mode", Boolean.TRUE);
   applyCard.invoke(null, ongoing85);
   check(ongoing85.B.getBackground() instanceof CapsuleImageDrawable, "NowBarOngoingCardView applies CapsuleImageDrawable in MODE_IMAGE");
   check(ongoing85.F.getBackground() != null && ongoing85.F.getBackground() != imgCardBorderOrig, "MODE_IMAGE installs tinted specular border on border stroke view");
   check(titleTv.getTextColors().getDefaultColor() == OneUiBlurSpec.DARK_CONTENT_COLOR, "0%-scrim bright photo switches compatible card text to dark charcoal");

   imgHookCfg.imageScrimPercent = 60;
   ongoing85.w = 1.0f;
   XposedHelpers.setAdditionalInstanceField(ongoing85, "nb_dozing", Boolean.TRUE);
   applyCard.invoke(null, ongoing85);
   Field cidVisField = CapsuleImageDrawable.class.getDeclaredField("visibility");
   cidVisField.setAccessible(true);
   check(Math.abs(cidVisField.getFloat(ongoing85.B.getBackground()) - ConfigManager.AOD_IMAGE_DIM_VISIBILITY) < 0.01f, "MODE_IMAGE dims capsule on AOD to 45% visibility for AMOLED protection when hideBgOnAod=false");
   check(titleTv.getTextColors().getDefaultColor() == 0xFFFFFFFF, "60% scrim restores white card text contrast");

   imgHookCfg.hideBgOnAod = true;
   applyCard.invoke(null, ongoing85);
   check(ongoing85.F.getBackground() == imgCardBorderOrig, "hideBgOnAod=true restores original border immediately on AOD");
   check(!(ongoing85.B.getBackground() instanceof CapsuleImageDrawable) || ongoing85.B.getBackground().getAlpha() == 0, "hideBgOnAod=true removes visible image capsule on AOD");

   ongoing85.w = 0.0f;
   XposedHelpers.setAdditionalInstanceField(ongoing85, "nb_dozing", Boolean.FALSE);
   imgHookCfg.hideBgOnAod = false;
   applyCard.invoke(null, ongoing85);
   check(ongoing85.F.getBackground() != null && ongoing85.F.getBackground() != imgCardBorderOrig, "waking from hideBgOnAod restores specular border");
   imgHookCfg.getCategory(ConfigManager.CAT_OTHER_ONGOING).enabled = false;
   applyCard.invoke(null, ongoing85);
   check(!(ongoing85.B.getBackground() instanceof CapsuleImageDrawable) && ongoing85.F.getBackground() == imgCardBorderOrig, "disabling category restores original card background and border");

   expandTestCard.l0 = false;
   expandTestCard.j0 = 0;
   XposedHelpers.setAdditionalInstanceField(expandTestCard, "nb_dark_amount", 0.0f);
   XposedHelpers.setAdditionalInstanceField(expandTestCard, "nb_dozing", Boolean.FALSE);
   XposedHelpers.setAdditionalInstanceField(expandTestCard, "nb_lockscreen_mode", Boolean.TRUE);
   p1Card.setBackground(new Drawable());
   applyCard.invoke(null, expandTestCard);
   check(p1Card.getBackground() instanceof CapsuleImageDrawable && o1Card.color == 0, "NowBarMediaCardView applies CapsuleImageDrawable on p1 and keeps o1 transparent");
   expandTestCard.l0 = true;
   expandTestCard.j0 = 1;
   applyCard.invoke(null, expandTestCard);
   check(!(p1Card.getBackground() instanceof CapsuleImageDrawable) && o1Card.color == 0, "expanded MediaCardView clears MODE_IMAGE drawable cleanly");

   // Regression: NowBarBatteryCardView with tall root container (> 145dp) must still customize across Solid, Blur, and Image modes
   com.samsung.android.app.facewidget.presentation.NowBarBatteryCardView batteryCard =
     new com.samsung.android.app.facewidget.presentation.NowBarBatteryCardView();
   batteryCard.heightPx = 600; // 200dp at density=3.0
   Drawable batOrigBg = new Drawable();
   Drawable batOrigBorder = new Drawable();
   batteryCard.B.setBackground(batOrigBg);
   batteryCard.F.setBackground(batOrigBorder);
   ConfigManager.Config batCfg = new ConfigManager.Config();
   batCfg.masterEnabled = true;
   batCfg.styleMode = ConfigManager.STYLE_SOLID;
   batCfg.colorMode = ConfigManager.MODE_FIXED_COLOR;
   batCfg.globalColor = 0xFF00897B;
   batCfg.opacity = 100;
   batCfg.getCategory(ConfigManager.CAT_BATTERY).enabled = true;
   config.set(null, batCfg);
   applyCard.invoke(null, batteryCard);
   check(batteryCard.B.getBackground() instanceof GradientDrawable && ((GradientDrawable) batteryCard.B.getBackground()).getColor().getDefaultColor() == 0xFF00897B, "NowBarBatteryCardView applies Solid color even when root height exceeds 145dp");
   check(Math.abs(batteryCard.q1.getAlpha() - 0.28f) < 0.01f, "NowBarBatteryCardView dims battery_progressbar to 28% in Solid mode");

   batCfg.styleMode = ConfigManager.STYLE_BLUR;
   batCfg.colorMode = ConfigManager.MODE_GRADIENT;
   batCfg.gradientStartColor = 0xFF1477FF;
   batCfg.gradientEndColor = 0xFF9D4EDD;
   batCfg.blurIntensity = 60;
   batCfg.blurTintOpacity = 60;
   applyCard.invoke(null, batteryCard);
   check(batteryCard.B.inspectBlur() != null && batteryCard.B.getForeground() instanceof GradientDrawable, "NowBarBatteryCardView applies Color Blur when root height exceeds 145dp");
   check(batteryCard.F.getBackground() != null && batteryCard.F.getBackground() != batOrigBorder, "NowBarBatteryCardView applies specular border in Color Blur");

   batCfg.colorMode = ConfigManager.MODE_IMAGE;
   batCfg.imageRev = 777L;
   batCfg.imageDomColor = 0xFF00BCD4;
   batCfg.imageLuma = 110;
   batCfg.imageScrimPercent = 40;
   batCfg.hideBgOnAod = false;
   applyCard.invoke(null, batteryCard);
   check(batteryCard.B.getBackground() instanceof CapsuleImageDrawable, "NowBarBatteryCardView applies CapsuleImageDrawable in MODE_IMAGE");
   check(Math.abs(batteryCard.q1.getAlpha() - 0.28f) < 0.01f, "NowBarBatteryCardView preserves 28% battery_progressbar alpha in MODE_IMAGE");

   batteryCard.w = 1.0f;
   batteryCard.u = true;
   applyCard.invoke(null, batteryCard);
   check(Math.abs(cidVisField.getFloat(batteryCard.B.getBackground()) - ConfigManager.AOD_IMAGE_DIM_VISIBILITY) < 0.01f, "NowBarBatteryCardView dims MODE_IMAGE capsule to 45% on AOD");
   check(Math.abs(batteryCard.q1.getAlpha() - (0.28f * ConfigManager.AOD_IMAGE_DIM_VISIBILITY)) < 0.01f, "NowBarBatteryCardView scales battery_progressbar alpha cleanly on AOD image dim");

   batCfg.hideBgOnAod = true;
   applyCard.invoke(null, batteryCard);
   check(batteryCard.q1.getAlpha() == 0.0f && !(batteryCard.B.getBackground() instanceof CapsuleImageDrawable), "NowBarBatteryCardView clears background and progress bar when hideBgOnAod=true");

   batteryCard.w = 0.0f;
   batteryCard.u = false;
   batCfg.hideBgOnAod = false;
   applyCard.invoke(null, batteryCard);
   batteryCard.expanded = true;
   applyCard.invoke(null, batteryCard);
   check(!(batteryCard.B.getBackground() instanceof CapsuleImageDrawable) && batteryCard.F.getBackground() == batOrigBorder && batteryCard.q1.getAlpha() == 1.0f, "expanding NowBarBatteryCardView restores original background, border, and progress bar alpha");
  } finally {
   adapterField.set(null, prevAdapter);
   hookMapField.set(null, prevHookMap);
   config.set(null, disabled);
  }

  // Pure Solid (<100% opacity) Regression Tests across Standard Frame-0, Media collapse, Wallpaper & GenUI
  ConfigManager.Config solid50 = new ConfigManager.Config();
  solid50.masterEnabled = true;
  solid50.styleMode = ConfigManager.STYLE_SOLID;
  solid50.colorMode = ConfigManager.MODE_FIXED_COLOR;
  solid50.globalColor = 0xff1477ff;
  solid50.opacity = 50;
  solid50.getCategory(ConfigManager.CAT_OTHER_ONGOING).enabled = true;
  solid50.getCategory(ConfigManager.CAT_MEDIA).enabled = true;
  solid50.getCategory(ConfigManager.CAT_BRIEF).enabled = true;
  solid50.getCategory(ConfigManager.CAT_BATTERY).enabled = true;
  int expectedSolid50Argb = solid50.resolveFinalArgbForCategory(ConfigManager.CAT_OTHER_ONGOING, null);
  check((expectedSolid50Argb >>> 24) == 0x80, "50% solid opacity resolves 0x80 alpha byte");

  Object prevWallpaperPal = hookField("sLivePalette").get(null);
  @SuppressWarnings("unchecked")
  java.util.Set<View> genUiBlurSet = (java.util.Set<View>) hookField("ACTIVE_GENUI_BLUR_VIEWS").get(null);
  @SuppressWarnings("unchecked")
  java.util.Set<View> genUiGlSet = (java.util.Set<View>) hookField("ACTIVE_GENUI_GUIDING_VIEWS").get(null);
  try {
   adapterField.set(null, OneUi85Adapter.INSTANCE);
   config.set(null, solid50);

   // 1. Standard capsule frame-0 with RoundRectCard + Samsung NowBarCardStackView.y(1.0f) -> setAlpha(255)
   com.samsung.android.app.facewidget.presentation.NowBarOngoingCardView stdOpacityCard =
     new com.samsung.android.app.facewidget.presentation.NowBarOngoingCardView();
   RoundRectCard stdBg = new RoundRectCard();
   stdOpacityCard.B = stdBg;
   stdOpacityCard.registerChild("nowbar_cardview_background", stdBg);
   Object stdUpdate = constructor.newInstance(stdOpacityCard);
   request.invoke(stdUpdate);
   stdOpacityCard.getViewTreeObserver().draw(); // Frame 0
   stdBg.getBackground().setAlpha(255); // Simulate Samsung NowBarCardStackView.y(1.0f, card)
   check(stdBg.getBackground() instanceof GradientDrawable, "Standard Frame-0 Pure Solid replaces RoundRectDrawable with GradientDrawable");
   check(((GradientDrawable) stdBg.getBackground()).getColor().getDefaultColor() == expectedSolid50Argb
     && stdBg.getBackground().getAlpha() == 255
     && (stdBg.nativeRoundRect.paintColor >>> 24) == 0,
     "Standard Frame-0 Pure Solid preserves 50% opacity after Samsung setAlpha(255)");

   // Standard Wallpaper Color at 45% opacity
   WallpaperColorHelper.PaletteResult pal50 = new WallpaperColorHelper.PaletteResult();
   pal50.primaryColor = 0xff22aa88;
   hookField("sLivePalette").set(null, pal50);
   solid50.colorMode = ConfigManager.MODE_LOCK_WALLPAPER;
   solid50.wallpaperVariant = ConfigManager.WALLPAPER_PRIMARY;
   solid50.opacity = 45;
   int expectedWall45Argb = ConfigManager.scaleAlpha(0xff22aa88, 0.45f);
   request.invoke(stdUpdate);
   stdOpacityCard.getViewTreeObserver().draw();
   stdBg.getBackground().setAlpha(255);
   check(((GradientDrawable) stdBg.getBackground()).getColor().getDefaultColor() == expectedWall45Argb
     && ((expectedWall45Argb >>> 24) == 0x73),
     "Wallpaper Color Pure Solid at 45% opacity preserves 0x73 ARGB alpha on Frame 0");

   // 2. NowBarMediaCardView at 40% opacity: Initial Frame-0 + Expand -> Animated Collapse (p8 setAlpha 0..255)
   solid50.colorMode = ConfigManager.MODE_FIXED_COLOR;
   solid50.globalColor = 0xff1477ff;
   solid50.opacity = 40;
   int expectedMedia40Argb = solid50.resolveFinalArgbForCategory(ConfigManager.CAT_MEDIA, null);
   check((expectedMedia40Argb >>> 24) == 0x66, "40% media opacity resolves 0x66 alpha byte");
   com.samsung.android.app.facewidget.presentation.NowBarMediaCardView mediaOpacityCard =
     new com.samsung.android.app.facewidget.presentation.NowBarMediaCardView();
   RoundRectCard mediaP1 = new RoundRectCard();
   RoundRectCard mediaO1 = new RoundRectCard();
   mediaOpacityCard.p1 = mediaP1;
   mediaOpacityCard.o1 = mediaO1;
   mediaOpacityCard.registerChild("stack_background_cardview", mediaP1);
   mediaOpacityCard.registerChild("nowbar_cardview_background", mediaO1);
   Object mediaOpacityUpd = constructor.newInstance(mediaOpacityCard);
   request.invoke(mediaOpacityUpd);
   mediaOpacityCard.getViewTreeObserver().draw();
   mediaP1.getBackground().setAlpha(255); // Simulate post-bind stack alpha=1.0
   check(mediaP1.getBackground() instanceof GradientDrawable
     && ((GradientDrawable) mediaP1.getBackground()).getColor().getDefaultColor() == expectedMedia40Argb
     && mediaP1.getBackground().getAlpha() == 255,
     "Media Frame-0 Pure Solid preserves 40% opacity after post-bind setAlpha(255)");

   // Expand media card -> restores RoundRectDrawable cleanly at TRANSPARENT
   mediaOpacityCard.l0 = true;
   mediaOpacityCard.j0 = 1;
   mediaOpacityCard.getViewTreeObserver().draw();
   check(mediaP1.getBackground() == mediaP1.nativeRoundRect && mediaP1.color == 0,
     "Media expand restores native RoundRectDrawable cleanly");

   // Collapse with p8 animation frames: setAlpha(0) -> draw -> setAlpha(128) -> draw -> setAlpha(255) -> draw
   mediaOpacityCard.l0 = false;
   mediaOpacityCard.j0 = 0;
   handleExpand.invoke(null, mediaOpacityCard, Boolean.FALSE, Boolean.TRUE);
   mediaP1.getBackground().setAlpha(0);
   mediaOpacityCard.getViewTreeObserver().draw();
   check(mediaP1.getBackground().getAlpha() == 255, "MediaLayer repairs p8 intermediate setAlpha(0) frame");
   mediaP1.getBackground().setAlpha(128);
   mediaOpacityCard.getViewTreeObserver().draw();
   check(mediaP1.getBackground().getAlpha() == 255, "MediaLayer repairs p8 intermediate setAlpha(128) frame");
   mediaP1.getBackground().setAlpha(255);
   mediaOpacityCard.getViewTreeObserver().draw();
   check(mediaP1.getBackground() instanceof GradientDrawable
     && ((GradientDrawable) mediaP1.getBackground()).getColor().getDefaultColor() == expectedMedia40Argb
     && mediaP1.getBackground().getAlpha() == 255,
     "Media post-collapse retains exact 40% ARGB opacity after final p8 setAlpha(255)");

   // 3. GenUI NowBarBriefCardView non-double-paint verification (Solid & Two-Stop Gradient at 50% opacity)
   Method applyCardOpacity = NowBarHook.class.getDeclaredMethod("applyOrRestoreCardBackground", View.class);
   applyCardOpacity.setAccessible(true);
   solid50.opacity = 50;
   int expectedBrief50Argb = solid50.resolveFinalArgbForCategory(ConfigManager.CAT_BRIEF, null);
   com.samsung.android.app.facewidget.presentation.NowBarBriefCardView genUiBrief =
     new com.samsung.android.app.facewidget.presentation.NowBarBriefCardView();
   RoundRectCard genUiBriefBg = new RoundRectCard();
   genUiBrief.briefBg = genUiBriefBg;
   genUiBrief.registerChild("nowbar_cardview_background_for_now_brief", genUiBriefBg);
   View genUiBlurView = new View();
   genUiBlurView.parent = genUiBrief;
   View genUiGlView = new View();
   genUiGlView.parent = genUiBrief;
   genUiBlurSet.add(genUiBlurView);
   genUiGlSet.add(genUiGlView);
   Method hasAnyGenUi = NowBarHook.class.getDeclaredMethod("hasAnyAttachedGenUiView");
   hasAnyGenUi.setAccessible(true);
   check(Boolean.TRUE.equals(hasAnyGenUi.invoke(null)), "hasAnyAttachedGenUiView detects attached GenUI overlay so Compose BaseStyle yields TRANSPARENT");
   applyCardOpacity.invoke(null, genUiBrief);
   String genUiDiag = " summary=" + hookField("sLastCardSummary").get(null)
     + " glAlpha=" + genUiGlView.getAlpha() + " glBg=" + genUiGlView.getBackground()
     + " blurBg=" + genUiBlurView.getBackground() + " briefBg=" + genUiBriefBg.getBackground();
   check(genUiBriefBg.color == 0 && !(genUiBriefBg.getBackground() instanceof GradientDrawable),
     "Brief briefBg stays transparent when attached GenUI overlay owns pill background:" + genUiDiag);
   check(genUiGlView.getAlpha() == 0.0f && genUiGlView.getBackground() == null,
     "Brief GuidingLight sibling stays hidden in Pure Solid when StyleBox.Blur sibling is attached:" + genUiDiag);
   check(genUiBlurView.getBackground() instanceof GradientDrawable
     && ((GradientDrawable) genUiBlurView.getBackground()).getColor().getDefaultColor() == expectedBrief50Argb
     && genUiBlurView.getBackground().getAlpha() == 255,
     "Brief GenUI blurView renders single clean 50% ARGB Solid layer");

   solid50.colorMode = ConfigManager.MODE_GRADIENT;
   solid50.gradientStartColor = 0xffff6600;
   solid50.gradientEndColor = 0xff3366ff;
   applyCardOpacity.invoke(null, genUiBrief);
   check(genUiGlView.getAlpha() == 0.0f && genUiGlView.getBackground() == null
     && genUiBriefBg.color == 0 && !(genUiBriefBg.getBackground() instanceof GradientDrawable),
     "Two-Stop Gradient also avoids GenUI glView and briefBg double-paint");
   checkGradient(genUiBlurView, ConfigManager.scaleAlpha(0xffff6600, 0.5f), ConfigManager.scaleAlpha(0xff3366ff, 0.5f),
     "Brief GenUI blurView renders Two-Stop Gradient at 50% opacity");

   // 4. NowBarBatteryCardView >145dp Pure Solid at 50% opacity remains intact
   solid50.colorMode = ConfigManager.MODE_FIXED_COLOR;
   solid50.globalColor = 0xff00c853;
   solid50.opacity = 50;
   int expectedBat50Argb = solid50.resolveFinalArgbForCategory(ConfigManager.CAT_BATTERY, null);
   com.samsung.android.app.facewidget.presentation.NowBarBatteryCardView batteryOpacityCard =
     new com.samsung.android.app.facewidget.presentation.NowBarBatteryCardView();
   batteryOpacityCard.heightPx = 600;
   applyCardOpacity.invoke(null, batteryOpacityCard);
   check(batteryOpacityCard.B.getBackground() instanceof GradientDrawable
     && ((GradientDrawable) batteryOpacityCard.B.getBackground()).getColor().getDefaultColor() == expectedBat50Argb,
     "NowBarBatteryCardView preserves 50% Pure Solid styling alongside progressBar");
  } finally {
   genUiBlurSet.clear();
   genUiGlSet.clear();
   hookField("sLivePalette").set(null, prevWallpaperPal);
   adapterField.set(null, prevAdapter);
   hookMapField.set(null, prevHookMap);
   config.set(null, disabled);
  }

  System.out.println("PASS "+checks+" assertions; blur getter="+getter);
 }
}'''
}


def run(args):
    subprocess.run([str(x) for x in args], check=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--android-jar', required=True)
    parser.add_argument('--source-root', type=Path, default=ROOT)
    args = parser.parse_args()
    jar = Path(args.android_jar).resolve()
    runtime_jars = sorted((args.source_root / 'libs' / 'runtime').glob('*.jar'))
    prod_cp = ':'.join([str(jar)] + [str(j) for j in runtime_jars])
    javac = ['javac'] if shutil.which('javac') else ['java', 'com.sun.tools.javac.Main']
    with tempfile.TemporaryDirectory(prefix='nowbar-tests-') as temp:
        temp = Path(temp)
        production = temp / 'production'
        production.mkdir()
        run(javac + ['-encoding','UTF-8','-source','8','-target','8','-cp',prod_cp,'-d',production] + sorted((args.source_root/'src').rglob('*.java')))
        for getter in (True, False):
            source = temp / ('with-getter' if getter else 'without-getter')
            output = source / 'classes'
            output.mkdir(parents=True)
            for name, text in DOUBLES.items():
                if not getter and name == 'android/view/View.java':
                    text = text.replace('public SemBlurInfo semGetBlurInfo(){if(failGetter)throw new IllegalStateException();return blur;}', '')
                path = source / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(text)
            cp = str(production) + ':' + str(jar)
            run(javac + ['-encoding','UTF-8','-cp',cp,'-d',output]+sorted(source.rglob('*.java')))
            run(['java','-cp',str(output)+':'+cp,'io.github.tavataan.nowbarcolor.HostTests',str(getter).lower()])
        # AOSP / incompatible ROM: optional Samsung blur API must fail harmlessly.
        view_file = source / 'android/view/View.java'
        text = view_file.read_text().replace('private SemBlurInfo blur;', '')
        text = text.replace('public void semSetBlurInfo(SemBlurInfo b){blurSets++;blur=b;if(b!=null)bg=blurBg;else if(bg==blurBg)bg=null;}', '')
        text = text.replace('public SemBlurInfo inspectBlur(){return blur;}', '')
        view_file.write_text(text)
        for path in output.glob('android/view/SemBlurInfo*.class'):
            path.unlink()
        smoke = source / 'io/github/tavataan/nowbarcolor/NoBlurTest.java'
        smoke.write_text("""package io.github.tavataan.nowbarcolor;
public class NoBlurTest {public static void main(String[] args){
android.view.View v=new android.view.View();
for(int i=0;i<100;i++)if(CapsuleStyle.blur(v,20,42))throw new AssertionError("unexpected blur support");
CapsuleStyle.gradient(v,1,2,20); Object gradient=v.getBackground();CapsuleStyle.clearBlur(v);
if(v.getBackground()!=gradient)throw new AssertionError("blur fallback erased gradient");
System.out.println("PASS missing Samsung blur API: 101 assertions");}}
""")
        run(javac + ['-cp',str(output)+':'+cp,'-d',output,view_file,smoke])
        run(['java','-cp',str(output)+':'+cp,'io.github.tavataan.nowbarcolor.NoBlurTest'])


if __name__ == '__main__':
    main()
