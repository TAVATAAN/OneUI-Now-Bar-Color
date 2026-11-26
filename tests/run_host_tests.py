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
public Context getApplicationContext(){return this;} public String getPackageName(){return "com.android.systemui";}
public ContentResolver getContentResolver(){return new ContentResolver();} }""",
'android/content/ContentResolver.java': """package android.content; public class ContentResolver {
public android.os.Bundle call(android.net.Uri u,String m,String a,android.os.Bundle b){return new android.os.Bundle();} }""",
'android/content/res/Configuration.java': '''package android.content.res;
public class Configuration { public static final int UI_MODE_NIGHT_MASK=48, UI_MODE_NIGHT_YES=32; public int uiMode=16; }''',
'android/content/res/Resources.java': '''package android.content.res;
import android.util.DisplayMetrics;
import java.util.HashMap;
public class Resources { public final Configuration config=new Configuration(); public final DisplayMetrics metrics=new DisplayMetrics(); {metrics.density=3.0f;}
 public Configuration getConfiguration(){return config;} public DisplayMetrics getDisplayMetrics(){return metrics;}
 private final HashMap<String,Integer> names=new HashMap<>(); private final HashMap<Integer,String> rev=new HashMap<>(); private int nextId=0x7d0a0100;
 public int registerId(String n){Integer cur=names.get(n); if(cur!=null)return cur; int id=nextId++; names.put(n,id); rev.put(id,n); return id;}
 public int getIdentifier(String n,String d,String p){Integer cur=names.get(n); return cur!=null?cur:0;}
 public String getResourceEntryName(int id){String s=rev.get(id); return s!=null?s:""; } }''',
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
  NativeCard nativeCard=new NativeCard(); CapsuleStyle.solid(nativeCard,42,30);
  for(int i=0;i<100;i++)CapsuleStyle.solid(nativeCard,42,30);
  check(nativeCard.sets==1,"native color setter deduplicated using live getter");
  nativeCard.setCardBackgroundColor(99); CapsuleStyle.solid(nativeCard,42,30);
  check(nativeCard.color==42 && nativeCard.sets==3,"native color change detected");
  nativeCard.throwGetter=true; CapsuleStyle.solid(nativeCard,43,30);
  check(nativeCard.color==43,"broken native getter still uses native setter");
  nativeCard.throwGetter=false; nativeCard.setBackground(null);
  CapsuleStyle.solid(nativeCard,43,30);
  check(nativeCard.getBackground()!=null,"native getter matches but detached background is repaired");
  CapsuleStyle.solid(nativeCard,44,30);
  check(((GradientDrawable)nativeCard.getBackground()).getColor().getDefaultColor()==44,"fallback tracks later native color updates");
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
  check(m96.getAlpha() == 255 && roundRectCard.color == 0xff1477ff, "wake solid restores RoundRectDrawable alpha cleanly");
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
  check(o1Card.color == 0 && o1Card.getElevation() == 0f && p1Card.color == expectedMediaArgb, "collapsed media clears o1 black fill/elevation and colors p1");

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
  check(p1Card.color == expectedMediaArgb && p1Card.getBackground().getAlpha() == 255 && o1Card.color == 0, "beforeCall collapse transition repaints p1 and restores alpha synchronously");

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
  check(o1Card.color == 0 && t1Card.color == 0 && t1Card.getForeground() == null && p1Card.color == 0, "wake -> expand with hideBgOnAod=true keeps o1, t1, and p1 transparent");

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
  } finally {
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
