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
 public View p1=new View(),o1=new View(),v1=new View(),w1=new View();
 public boolean isExpanded(){return l0;} public int getCardState(){return j0;}
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
public ContentResolver getContentResolver(){return new ContentResolver();} }""",
'android/content/ContentResolver.java': """package android.content; public class ContentResolver {
public android.os.Bundle call(android.net.Uri u,String m,String a,android.os.Bundle b){return new android.os.Bundle();} }""",
'android/provider/Settings.java': """package android.provider; public class Settings {
public static class System { public static String getString(android.content.ContentResolver r,String k){return "{\\\"masterEnabled\\\":false}";} }
public static class Global { public static String getString(android.content.ContentResolver r,String k){return null;} } }""",
'android/util/Log.java': """package android.util; public class Log { public static int e(String t,String m,Throwable e){throw new AssertionError(m,e);} }""",
'android/content/res/ColorStateList.java': '''package android.content.res;
public class ColorStateList { private int color; public ColorStateList(int c){color=c;}
public boolean isStateful(){return false;} public int getDefaultColor(){return color;} }''',
'android/graphics/ColorFilter.java': 'package android.graphics; public class ColorFilter {}',
'android/graphics/drawable/Drawable.java': '''package android.graphics.drawable;
import android.content.res.ColorStateList; import android.graphics.*;
public class Drawable {
 private int alpha=255; private boolean visible=true; private ColorFilter filter; private ColorStateList tint;
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
import android.graphics.drawable.Drawable;
import java.util.ArrayList;
public class View {
 public interface OnAttachStateChangeListener { void onViewAttachedToWindow(View v); void onViewDetachedFromWindow(View v); }
 private final ArrayList<OnAttachStateChangeListener> attach=new ArrayList<>();
 private ViewTreeObserver tree=new ViewTreeObserver();
 public static final int VISIBLE=0,INVISIBLE=4,GONE=8;
 private int visibility=VISIBLE; private float alpha=1f;
 public int getVisibility(){return visibility;} public void setVisibility(int v){visibility=v;}
 public float getAlpha(){return alpha;} public void setAlpha(float a){alpha=a;}
 public void setForeground(Drawable d){}
 public boolean failGetter; public boolean attached=true; public int invalidations, backgrounds, blurSets; private Drawable bg; private boolean clip;
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
 public void semSetBlurInfo(SemBlurInfo b){blurSets++;blur=b;}
 public SemBlurInfo semGetBlurInfo(){if(failGetter)throw new IllegalStateException();return blur;}
 public void detach(){attached=false;for(OnAttachStateChangeListener l:attach)l.onViewDetachedFromWindow(this);tree.alive=false;}
 public void attach(){tree=new ViewTreeObserver();attached=true;for(OnAttachStateChangeListener l:attach)l.onViewAttachedToWindow(this);}
}''',
'android/view/SemBlurInfo.java': '''package android.view;
public class SemBlurInfo {
 public static int builds;
 public static class Builder {
  public Builder(int mode){} public Builder setColorCurvePreset(int v){return this;}
  public Builder setBackgroundCornerRadius(float v){return this;}
  public Builder setBackgroundColor(int c){return this;}
  public SemBlurInfo build(){builds++;return new SemBlurInfo();}
 }
}''',
'android/view/ViewGroup.java': '''package android.view; public class ViewGroup extends View {
 public int getChildCount(){return 0;} public View getChildAt(int i){return null;} }''',
'android/widget/TextView.java': '''package android.widget; public class TextView extends android.view.View {
 public CharSequence getText(){return "";} }''',
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

  View failingGetter=new View();failingGetter.failGetter=true;
  check(CapsuleStyle.blur(failingGetter,30,42),"throwing getter falls back to setter");
  int beforeFallback=failingGetter.blurSets;
  check(CapsuleStyle.blur(failingGetter,30,42) && failingGetter.blurSets==beforeFallback+1,"failed getter uses conservative reapply");

  Field config=NowBarHook.class.getDeclaredField("sConfig");config.setAccessible(true);
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
   check(music.p1.getBackground()!=null,"initial media background, mode="+mode);
   for(int cycle=0;cycle<5;cycle++) {
    music.l0=true; music.j0=1; music.getViewTreeObserver().draw();
    music.p1.setVisibility(View.GONE);music.o1.setVisibility(View.VISIBLE);
    music.l0=false;music.j0=0;music.getViewTreeObserver().draw();
    check(music.o1.getBackground()!=null,"collapse repaired without hook/QS, mode="+mode);
    // Samsung switches the layer after the first collapsed frame, without another hook.
    music.p1.setVisibility(View.VISIBLE);music.getViewTreeObserver().draw();
    check(music.p1.getBackground()!=null && music.o1.getBackground()==null,"late layer switch repaired, mode="+mode);
    music.p1.setAlpha(0f);music.getViewTreeObserver().draw();
    check(music.o1.getBackground()!=null,"transparent stack uses normal layer, mode="+mode);
    music.p1.setAlpha(1f);music.getViewTreeObserver().draw();
    check(music.p1.getBackground()!=null,"stack alpha restored, mode="+mode);
    music.p1.setBackground(null);music.getViewTreeObserver().draw();
    check(music.p1.getBackground()!=null,"late native background removal repaired, mode="+mode);
   }
   if(mode>=2) {
    int[] expected=mediaConfig.resolveGradientArgbForCategory(ConfigManager.CAT_MEDIA);
    Drawable stableGradient=music.p1.getBackground();
    stableGradient.setAlpha(0);music.getViewTreeObserver().draw();
    checkGradient(music.p1,expected[0],expected[1],"late alpha mutation without hook");
    check(music.p1.getBackground()==stableGradient,"late repair reuses original gradient");
    stableGradient.setTint(0);music.getViewTreeObserver().draw();
    checkGradient(music.p1,expected[0],expected[1],"late transparent tint without hook");
    ((GradientDrawable)stableGradient).setColor(0);music.getViewTreeObserver().draw();
    checkGradient(music.p1,expected[0],expected[1],"late transparent color without hook");
    music.p1.setBackgroundTintList(new ColorStateList(0));music.getViewTreeObserver().draw();
    checkGradient(music.p1,expected[0],expected[1],"late view tint without hook");
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
     checkGradient(music.p1,expected[0],expected[1],"collapse after mutation while expanded");
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
    javac = ['javac'] if shutil.which('javac') else ['java', 'com.sun.tools.javac.Main']
    with tempfile.TemporaryDirectory(prefix='nowbar-tests-') as temp:
        temp = Path(temp)
        production = temp / 'production'
        production.mkdir()
        run(javac + ['-encoding','UTF-8','-source','8','-target','8','-cp',jar,'-d',production] + sorted((args.source_root/'src').rglob('*.java')))
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
        text = text.replace('public void semSetBlurInfo(SemBlurInfo b){blurSets++;blur=b;}', '')
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
