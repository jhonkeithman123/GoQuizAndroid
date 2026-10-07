package com.goquiz.adventure;

import android.app.*;
import android.os.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.content.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import android.media.*;
import android.content.res.AssetFileDescriptor;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    static class Question { String text; String[] choices; int answer; Question(String t,String[] c,int a){text=t;choices=c;answer=a;} }
    static class Account { String name,grade,section,gender,password; Account(String n,String g,String s,String ge,String p){name=n;grade=g;section=s;gender=ge;password=p;} }

    final String[] LANGUAGES={"HTML","CSS","JavaScript","Java"};
    final String[] DIFFICULTIES={"Easy","Medium","Hard"};
    final int GOLD=Color.rgb(231,160,39), GOLD_LIGHT=Color.rgb(255,202,78), BG=Color.rgb(14,22,31), TEXT=Color.rgb(245,241,226), MUTED=Color.rgb(192,190,180);
    SharedPreferences prefs; Map<String,ArrayList<ArrayList<Question>>> bank=new LinkedHashMap<>();
    String studentName="",grade="",section="",gender="",difficulty="Medium",language="";
    int level=1,questionIndex=0,score=0,hearts=3; ArrayList<Question> current=new ArrayList<>();
    ExecutorService io=Executors.newSingleThreadExecutor();
    static volatile QuizServer embeddedServer;
    static volatile Thread embeddedServerThread;
    SoundPool soundPool; int clickSoundId=-1, damageSoundId=-1; MediaPlayer mediaPlayer; boolean soundEnabled=true;

    @Override public void onCreate(Bundle b){super.onCreate(b); prefs=getSharedPreferences("goquiz",MODE_PRIVATE); loadBank(); initAudio(); showLogin();}
    @Override protected void onResume(){super.onResume(); if(soundEnabled) startMusic();}
    @Override protected void onPause(){super.onPause(); stopMusic();}
    @Override protected void onDestroy(){if(embeddedServer!=null){embeddedServer.stop();embeddedServer=null;}if(mediaPlayer!=null){try{mediaPlayer.release();}catch(Exception ignored){}mediaPlayer=null;}if(soundPool!=null){try{soundPool.release();}catch(Exception ignored){}soundPool=null;}io.shutdownNow();super.onDestroy();}

    void initAudio(){
        soundEnabled=prefs.getBoolean("soundEnabled",true);
        try{
            AudioAttributes attrs=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
            soundPool=new SoundPool.Builder().setMaxStreams(5).setAudioAttributes(attrs).build();
            AssetFileDescriptor cFd=getAssets().openFd("sounds/click.wav");
            clickSoundId=soundPool.load(cFd,1);
            AssetFileDescriptor dFd=getAssets().openFd("sounds/damage.wav");
            damageSoundId=soundPool.load(dFd,1);
        }catch(Exception ignored){}
        startMusic();
    }
    void startMusic(){if(!soundEnabled)return;try{if(mediaPlayer==null){mediaPlayer=new MediaPlayer();AssetFileDescriptor afd=getAssets().openFd("sounds/menu_theme.wav");mediaPlayer.setDataSource(afd.getFileDescriptor(),afd.getStartOffset(),afd.getLength());mediaPlayer.setLooping(true);mediaPlayer.prepare();}if(!mediaPlayer.isPlaying())mediaPlayer.start();}catch(Exception ignored){}}
    void stopMusic(){try{if(mediaPlayer!=null&&mediaPlayer.isPlaying())mediaPlayer.pause();}catch(Exception ignored){}}
    void playClick(){if(!soundEnabled||soundPool==null||clickSoundId==-1)return;soundPool.play(clickSoundId,1f,1f,0,0,1f);}
    void playDamage(){if(!soundEnabled||soundPool==null||damageSoundId==-1)return;soundPool.play(damageSoundId,1f,1f,0,0,1f);}

    TextView title(String s,int size){TextView v=new TextView(this);v.setText(s);v.setTextColor(TEXT);v.setTextSize(size);v.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);v.setGravity(Gravity.CENTER);v.setPadding(12,18,12,18);return v;}
    Button btn(String s){Button b=new Button(this);b.setText(s);b.setTextColor(TEXT);b.setTextSize(16);b.setAllCaps(false);b.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);b.setBackgroundColor(Color.rgb(35,47,59));b.setPadding(12,10,12,10);b.setOnTouchListener((v,ev)->{if(ev.getAction()==MotionEvent.ACTION_DOWN)playClick();return false;});return b;}
    LinearLayout page(){LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(18,18,18,18);root.setBackgroundColor(BG);ScrollView sc=new ScrollView(this);sc.addView(root);setContentView(sc);return root;}
    void add(LinearLayout p,View v){p.addView(v,new LinearLayout.LayoutParams(-1,ViewGroup.LayoutParams.WRAP_CONTENT));}
    void addSpace(LinearLayout p,int h){Space s=new Space(this);p.addView(s,new LinearLayout.LayoutParams(1,h));}
    EditText input(String hint){EditText e=new EditText(this);e.setHint(hint);e.setHintTextColor(MUTED);e.setTextColor(TEXT);e.setSingleLine(true);e.setTextSize(16);return e;}
    Spinner spinner(String[] vals){Spinner sp=new Spinner(this);ArrayAdapter<String>a=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,vals);sp.setAdapter(a);return sp;}
    void message(String s){new AlertDialog.Builder(this).setMessage(s).setPositiveButton("OK",null).show();}

    void showLogin(){
        LinearLayout p=page(); add(p,title("GOQUIZ ADVENTURE",30)); add(p,title("STUDENT LOGIN",25)); add(p,title("ENTER YOUR ACCOUNT TO CONTINUE",14));
        EditText name=input("Student Name"), pass=input("Password"); pass.setInputType(0x81); add(p,name);add(p,pass);addSpace(p,10);
        Button login=btn("LOGIN"); add(p,login); Button register=btn("CREATE ACCOUNT");add(p,register);
        login.setOnClickListener(v->{String n=name.getText().toString().trim(),pw=pass.getText().toString();if(n.isEmpty()||pw.isEmpty()){message("Enter your student name and password.");return;}String raw=prefs.getString("acct_"+n.toLowerCase(Locale.ROOT),null);if(raw==null){message("Account not found.");return;}String[] a=raw.split("\\|",-1);if(a.length<5||!a[4].equals(pw)){message("Incorrect password.");return;}apply(new Account(a[0],a[1],a[2],a[3],a[4]));showHome();});
        register.setOnClickListener(v->showRegister());
    }
    void showRegister(){
        LinearLayout p=page();add(p,title("GOQUIZ ADVENTURE",28));add(p,title("CREATE ACCOUNT",24));
        EditText n=input("Student Name"), sec=input("Section"), pw=input("Password");pw.setInputType(0x81);Spinner gr=spinner(new String[]{"Grade 11","Grade 12"}), ge=spinner(new String[]{"Male","Female","Prefer not to say"});
        add(p,n);add(p,gr);add(p,sec);add(p,ge);add(p,pw);Button create=btn("REGISTER");Button back=btn("BACK");add(p,create);add(p,back);
        create.setOnClickListener(v->{String nm=n.getText().toString().trim(),ss=sec.getText().toString().trim(),pp=pw.getText().toString();if(nm.isEmpty()||ss.isEmpty()||pp.isEmpty()){message("Complete all required fields.");return;}String key="acct_"+nm.toLowerCase(Locale.ROOT);if(prefs.contains(key)){message("That student name is already registered.");return;}String val=nm+"|"+gr.getSelectedItem()+"|"+ss+"|"+ge.getSelectedItem()+"|"+pp;prefs.edit().putString(key,val).apply();apply(new Account(nm,gr.getSelectedItem().toString(),ss,ge.getSelectedItem().toString(),pp));showHome();});back.setOnClickListener(v->showLogin());
    }
    void apply(Account a){studentName=a.name;grade=a.grade;section=a.section;gender=a.gender;loadProgress();}
    String pk(String d,String l){return "progress|"+studentName+"|"+d+"|"+l;}
    int progress(String d,String l){return prefs.getInt(pk(d,l),0);}
    void saveProgress(String d,String l,int n){prefs.edit().putInt(pk(d,l),Math.max(progress(d,l),n)).apply();}
    void loadProgress(){}

    void showHome(){LinearLayout p=page();add(p,title("GOQUIZ ADVENTURE",32));add(p,title("Welcome, "+studentName,18));add(p,title(grade+" • "+section,14));addSpace(p,10);
        Button play=btn("PLAY"),badges=btn("BADGES"),leader=btn("LEADERBOARD"),settings=btn("SETTINGS");add(p,play);add(p,badges);add(p,leader);add(p,settings);
        play.setOnClickListener(v->showDifficulty());badges.setOnClickListener(v->showBadges());leader.setOnClickListener(v->showLeaderboard());settings.setOnClickListener(v->showSettings());}
    void showDifficulty(){LinearLayout p=page();add(p,title("CHOOSE DIFFICULTY",28));add(p,title("Select the challenge level",16));for(String d:DIFFICULTIES){Button b=btn(d+ (d.equals("Easy")?" • 4 ♥":d.equals("Medium")?" • 3 ♥":" • 2 ♥"));add(p,b);b.setOnClickListener(v->{difficulty=d;showLanguages();});}Button back=btn("BACK");add(p,back);back.setOnClickListener(v->showHome());}
    void showLanguages(){LinearLayout p=page();add(p,title("CHOOSE LANGUAGE",28));for(String l:LANGUAGES){if(grade.equals("Grade 11")&&l.equals("Java"))continue;Button b=btn(l);add(p,b);b.setOnClickListener(v->{language=l;showLevels();});}Button back=btn("BACK");add(p,back);back.setOnClickListener(v->showDifficulty());}
    void showLevels(){LinearLayout p=page();add(p,title(language+" • "+difficulty,26));int done=progress(difficulty,language);for(int i=1;i<=5;i++){int lv=i;Button b=btn("LEVEL "+i+(done>=i?" • ★ COMPLETED":done>=i-1?" • "+levelText(i):" • LOCKED"));b.setEnabled(i==1||done>=i-1);add(p,b);b.setOnClickListener(v->startLevel(lv));}Button back=btn("BACK");add(p,back);back.setOnClickListener(v->showLanguages());}
    String levelText(int l){return new String[]{"","BEGINNER","EASY","INTERMEDIATE","ADVANCED","EXPERT"}[l];}
    int sourceLevel(String d,int l){if(d.equals("Easy"))return l<=2?1:l<=4?2:3;if(d.equals("Medium"))return l==1?2:l<=3?3:4;return l==1?3:l<=3?4:5;}
    int heartsFor(){return difficulty.equals("Easy")?4:difficulty.equals("Medium")?3:2;}
    int points(){return (difficulty.equals("Easy")?5:difficulty.equals("Hard")?15:10)*level;}
    void startLevel(int lv){level=lv;questionIndex=0;score=0;hearts=heartsFor();current.clear();ArrayList<ArrayList<Question>> levels=bank.get(language);int tier=sourceLevel(difficulty,lv)-1;if(levels!=null&&tier>=0&&tier<levels.size())current.addAll(levels.get(tier));Collections.shuffle(current);showQuestion();}
    void showQuestion(){if(questionIndex>=current.size()){complete();return;}LinearLayout p=page();LinearLayout hud=new LinearLayout(this);hud.setGravity(Gravity.CENTER);TextView h=title(heartsString(),18),info=title(language+" • "+difficulty.toUpperCase(Locale.ROOT)+" • L"+level+" • "+(questionIndex+1)+"/"+current.size(),15),sc=title("Score: "+score,18);hud.addView(h,new LinearLayout.LayoutParams(0,-2,1));hud.addView(info,new LinearLayout.LayoutParams(0,-2,2));hud.addView(sc,new LinearLayout.LayoutParams(0,-2,1));add(p,hud);Question q=current.get(questionIndex);add(p,title(q.text,21));for(int i=0;i<4;i++){int pick=i;Button b=btn(((char)('A'+i))+". "+q.choices[i]);add(p,b);b.setOnClickListener(v->answer(pick));} }
    String heartsString(){StringBuilder s=new StringBuilder();for(int i=0;i<hearts;i++)s.append("♥ ");return s.toString();}
    void answer(int selected){Question q=current.get(questionIndex);if(selected==q.answer){playClick();score+=points();sendScore();questionIndex++;showQuestion();}else{playDamage();hearts--;if(hearts<=0){new AlertDialog.Builder(this).setTitle("Level Failed").setMessage("You ran out of hearts. Try the level again.").setPositiveButton("OK",(d,w)->showLevels()).show();}else{message("Wrong answer! You lost a heart.");questionIndex++;showQuestion();}}}
    void complete(){saveProgress(difficulty,language,Math.max(progress(difficulty,language),level));sendScore();new AlertDialog.Builder(this).setTitle("Level Complete!").setMessage("You completed Level "+level+" with "+score+" points.").setPositiveButton("OK",(d,w)->showLevels()).show();}

    void showBadges(){LinearLayout p=page();add(p,title("BADGES & PROGRESS",28));for(String l:LANGUAGES){if(grade.equals("Grade 11")&&l.equals("Java"))continue;LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(14,12,14,12);card.setBackgroundColor(Color.rgb(24,35,46));TextView t=title(l,20);card.addView(t);for(String d:DIFFICULTIES){TextView x=title(d+" — "+progress(d,l)+"/5",16);card.addView(x);}p.addView(card,new LinearLayout.LayoutParams(-1,-2));addSpace(p,10);}Button back=btn("BACK");add(p,back);back.setOnClickListener(v->showHome());}
    void showSettings(){LinearLayout p=page();add(p,title("SETTINGS",28));add(p,title("Student: "+studentName,16));add(p,title("Grade: "+grade,16));Button soundBtn=btn(soundEnabled?"SOUND: ON 🔊":"SOUND: OFF 🔇");add(p,soundBtn);soundBtn.setOnClickListener(v->{soundEnabled=!soundEnabled;prefs.edit().putBoolean("soundEnabled",soundEnabled).apply();if(soundEnabled)startMusic();else stopMusic();soundBtn.setText(soundEnabled?"SOUND: ON 🔊":"SOUND: OFF 🔇");});Button logout=btn("LOG OUT");Button back=btn("BACK");add(p,logout);add(p,back);logout.setOnClickListener(v->{studentName="";showLogin();});back.setOnClickListener(v->showHome());}

    void showLeaderboard(){LinearLayout p=page();add(p,title("ONLINE LEADERBOARD",28));Button hostBtn=btn(embeddedServer!=null?"STOP LOCAL SERVER":"HOST SERVER ON THIS DEVICE");add(p,hostBtn);hostBtn.setOnClickListener(v->{if(embeddedServer!=null){embeddedServer.stop();embeddedServer=null;hostBtn.setText("HOST SERVER ON THIS DEVICE");message("Local server stopped.");}else{embeddedServer=new QuizServer(5050,getFilesDir());embeddedServerThread=new Thread(()->{try{embeddedServer.start();}catch(Exception ignored){}});embeddedServerThread.setDaemon(true);embeddedServerThread.start();hostBtn.setText("STOP LOCAL SERVER");message("Server running on port 5050!\nOther devices on this Wi-Fi/Hotspot can connect.");}});addSpace(p,10);EditText host=input("Server IP (example: 192.168.1.10)");host.setText(prefs.getString("serverHost","127.0.0.1"));EditText port=input("Port");port.setText("5050");add(p,host);add(p,port);Button refresh=btn("CONNECT / REFRESH");TextView board=title("Connecting...",16);add(p,refresh);add(p,board);Button back=btn("BACK");add(p,back);back.setOnClickListener(v->showHome());refresh.setOnClickListener(v->{prefs.edit().putString("serverHost",host.getText().toString().trim()).apply();board.setText("Loading...");io.submit(()->{String r=serverRequest(host.getText().toString().trim(),Integer.parseInt(port.getText().toString().trim()),"SCORE|"+clean(studentName)+"|"+score);String b=serverRequest(host.getText().toString().trim(),Integer.parseInt(port.getText().toString().trim()),"LEADERBOARD");runOnUiThread(()->board.setText(formatBoard(b)));});});}
    String clean(String s){return s.replace('|',' ');}
    String serverRequest(String host,int port,String msg){try(Socket s=new Socket();){s.connect(new InetSocketAddress(host,port),2500);BufferedWriter out=new BufferedWriter(new OutputStreamWriter(s.getOutputStream()));BufferedReader in=new BufferedReader(new InputStreamReader(s.getInputStream()));out.write(msg);out.newLine();out.flush();return in.readLine();}catch(Exception e){return "ERROR|"+e.getMessage();}}
    String formatBoard(String raw){if(raw==null)return "No response.";if(raw.startsWith("ERROR"))return raw;String[] p=raw.split("\\|",-1);StringBuilder b=new StringBuilder();int rank=1;for(int i=1;i+1<p.length;i+=2)b.append(rank++).append(". ").append(p[i]).append(" — ").append(p[i+1]).append(" points\n");return b.length()==0?"No scores yet.":b.toString();}
    void sendScore(){String host=prefs.getString("serverHost","127.0.0.1");io.submit(()->serverRequest(host,5050,"SCORE|"+clean(studentName)+"|"+score));}

    void loadBank(){try{InputStream in=getAssets().open("questions.json");BufferedReader r=new BufferedReader(new InputStreamReader(in));StringBuilder s=new StringBuilder();String line;while((line=r.readLine())!=null)s.append(line);JSONObject root=new JSONObject(s.toString());for(String lang:LANGUAGES){JSONArray levels=root.getJSONArray(lang);ArrayList<ArrayList<Question>> ls=new ArrayList<>();for(int i=0;i<levels.length();i++){JSONArray arr=levels.getJSONArray(i);ArrayList<Question> qs=new ArrayList<>();for(int j=0;j<arr.length();j++){JSONObject q=arr.getJSONObject(j);JSONArray c=q.getJSONArray("choices");qs.add(new Question(q.getString("q"),new String[]{c.getString(0),c.getString(1),c.getString(2),c.getString(3)},q.getInt("answer")));}ls.add(qs);}bank.put(lang,ls);}}catch(Exception e){message("Could not load the question bank: "+e.getMessage());}}
}
