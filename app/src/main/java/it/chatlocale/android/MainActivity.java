package it.chatlocale.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/**
 * Chat Locale Android - protocollo compatibile con ChatReteMultiV8 PowerShell.
 * Frame: 1 byte tipo + 4 byte lunghezza little-endian + payload UTF-8/binario.
 */
public class MainActivity extends Activity {
    private static final byte T_HELLO=1, T_TESTO=2, T_FSTART=3, T_FDATA=4,
            T_FEND=5, T_FABORT=6, T_BYE=7, T_PRIV=8, T_PING=9;
    private static final int PORT_DEFAULT=5050;
    private final Handler ui = new Handler();
    private final ExecutorService workers = Executors.newCachedThreadPool();
    private final List<Peer> peers = Collections.synchronizedList(new ArrayList<Peer>());
    private final Set<String> connecting = Collections.synchronizedSet(new HashSet<String>());
    private final Map<String,String> saved = new LinkedHashMap<String,String>();
    private volatile boolean active=false;
    private ServerSocket server;
    private String myName="Android", code="1234";
    private int port=PORT_DEFAULT;
    private EditText message, ip, name, pin, portBox;
    private Spinner recipients;
    private TextView log, myIp, progress;
    private LinearLayout root;
    private ArrayAdapter<String> recipientAdapter;
    private String receiveDir;

    private class Peer {
        Socket socket; DataInputStream in; DataOutputStream out;
        String name, host; final Object writeLock=new Object();
    }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        receiveDir = new File(Environment.getExternalStorageDirectory(),"ChatRicevuti").getAbsolutePath();
        loadPrefs(); buildUi(); refreshIp();
        append("Pronto. Premi Attiva Chat per iniziare.");
    }

    private TextView label(String s) {
        TextView t=new TextView(this); t.setText(s); t.setTextSize(14); t.setPadding(2,5,2,2); return t;
    }
    private EditText edit(String value, String hint, int type) {
        EditText e=new EditText(this); e.setSingleLine(true); e.setText(value); e.setHint(hint);
        e.setInputType(type); e.setTextSize(14); return e;
    }
    private Button button(String text, View.OnClickListener l) {
        Button b=new Button(this); b.setText(text); b.setOnClickListener(l); return b;
    }
    private void addRow(LinearLayout parent, View... views) {
        LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        for(View v:views) row.addView(v,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        parent.addView(row,new LinearLayout.LayoutParams(-1,ViewGroup.LayoutParams.WRAP_CONTENT));
    }
    private void buildUi() {
        ScrollView sc=new ScrollView(this); root=new LinearLayout(this); root.setPadding(8,6,8,8);
        root.setOrientation(LinearLayout.VERTICAL); sc.addView(root); setContentView(sc);
        TextView title=label("CHAT LOCALE MULTI-PC"); title.setTextSize(19); title.setGravity(Gravity.CENTER); root.addView(title);
        myIp=label("Mio IP: ..."); root.addView(myIp);
        log=new TextView(this); log.setTextSize(14); log.setTextColor(0xff17365d);
        log.setBackgroundColor(0xfff4f4f4); log.setPadding(8,8,8,8);
        ScrollView logScroll=new ScrollView(this); logScroll.addView(log);
        root.addView(logScroll,new LinearLayout.LayoutParams(-1,0,1));
        root.addView(label("Messaggio"));
        message=edit("","Scrivi un messaggio",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        addRow(root,message,button("Invia",new View.OnClickListener(){public void onClick(View v){sendText();}}));
        root.addView(label("Destinatario"));
        recipientAdapter=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,new ArrayList<String>());
        recipientAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        recipients=new Spinner(this); recipients.setAdapter(recipientAdapter); recipientAdapter.add("Tutti i PC");
        root.addView(recipients);
        root.addView(label("IP del PC da aggiungere"));
        ip=edit("","es. 192.168.1.10",InputType.TYPE_CLASS_TEXT);
        addRow(root,ip,button("Aggiungi PC",new View.OnClickListener(){public void onClick(View v){connectFromField();}}));
        root.addView(label("Nome dispositivo"));
        name=edit(myName,"Nome",InputType.TYPE_CLASS_TEXT);
        root.addView(name);
        root.addView(label("Codice di accesso"));
        pin=edit(code,"Codice",InputType.TYPE_CLASS_TEXT);
        root.addView(pin);
        root.addView(label("Porta TCP"));
        portBox=edit(String.valueOf(port),"5050",InputType.TYPE_CLASS_NUMBER);
        root.addView(portBox);
        addRow(root,button("Attiva Chat",new View.OnClickListener(){public void onClick(View v){startChat();}}),
                button("Disconnetti",new View.OnClickListener(){public void onClick(View v){stopChat();}}));
        addRow(root,button("Invia File",new View.OnClickListener(){public void onClick(View v){chooseFile();}}),
                button("Cartella Ricevuti",new View.OnClickListener(){public void onClick(View v){openReceived();}}));
        progress=label(""); root.addView(progress);
    }

    private void append(final String s) {
        ui.post(new Runnable(){public void run(){
            String time=new SimpleDateFormat("HH:mm:ss",Locale.ITALY).format(new Date());
            log.append("["+time+"] "+s+"\n");
        }});
    }
    private void refreshIp() {
        String found="non disponibile";
        try {
            Enumeration<NetworkInterface> en=NetworkInterface.getNetworkInterfaces();
            while(en.hasMoreElements()) {
                NetworkInterface ni=en.nextElement();
                if(!ni.isUp() || ni.isLoopback()) continue;
                Enumeration<InetAddress> aa=ni.getInetAddresses();
                while(aa.hasMoreElements()) {
                    InetAddress a=aa.nextElement();
                    if(a instanceof Inet4Address && !a.isLoopbackAddress()) { found=a.getHostAddress(); break; }
                }
                if(!"non disponibile".equals(found)) break;
            }
        } catch(Exception ignored) {}
        myIp.setText("Mio IP: "+found+"    Porta: "+port);
    }
    private void startChat() {
        try {
            myName=name.getText().toString().trim(); code=pin.getText().toString();
            port=Integer.parseInt(portBox.getText().toString());
            if(myName.length()==0 || port<1024 || port>65535 || code.length()==0) throw new Exception("Controlla nome, codice e porta.");
            if(active) stopChat();
            server=new ServerSocket(); server.setReuseAddress(true); server.bind(new InetSocketAddress(port));
            active=true; savePrefs(); refreshIp();
            append("In ascolto sulla porta "+port+". Pronto.");
            workers.execute(new Runnable(){public void run(){acceptLoop();}});
            startReconnectLoop();
        } catch(Exception e) { append("Errore avvio server: "+e.getMessage()); active=false; }
    }
    private void acceptLoop() {
        while(active) {
            try { final Socket s=server.accept(); workers.execute(new Runnable(){public void run(){acceptPeer(s);}}); }
            catch(Exception e) { if(active) append("Errore ascolto: "+e.getMessage()); break; }
        }
    }
    private void acceptPeer(Socket s) {
        try {
            s.setTcpNoDelay(true); s.setSoTimeout(5000);
            DataInputStream in=new DataInputStream(new BufferedInputStream(s.getInputStream()));
            DataOutputStream out=new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
            Frame hello=readFrame(in,1024);
            if(hello==null || hello.type!=T_HELLO) { s.close(); return; }
            String[] h=new String(hello.data,"UTF-8").split("\n",2);
            if(h.length<2 || !code.equals(h[1]) || !active) { s.close(); return; }
            synchronized(out) { writeFrame(out,T_HELLO,(myName+"\n"+code).getBytes("UTF-8")); }
            s.setSoTimeout(0);
            Peer p=new Peer(); p.socket=s;p.in=in;p.out=out;p.name=h[0];p.host=s.getInetAddress().getHostAddress();
            if(!register(p)){s.close();return;}
            append("--> ENTRATO IN CHAT: "+p.name+" ("+p.host+")"); receiveLoop(p);
        } catch(Exception e) { try{s.close();}catch(Exception ignored){} }
    }
    private void connectFromField() {
        final String host=ip.getText().toString().trim();
        if(host.length()==0)return;
        if(!active) startChat();
        saved.put(host,host);
        savePrefs();
        connectTo(host);
    }
    private void connectTo(final String host) {
        if(!active || connecting.contains(host) || hasPeer(host)) return;
        connecting.add(host);
        workers.execute(new Runnable(){public void run(){
            Socket s=new Socket();
            try {
                s.connect(new InetSocketAddress(host,port),2000); s.setTcpNoDelay(true); s.setSoTimeout(5000);
                DataInputStream in=new DataInputStream(new BufferedInputStream(s.getInputStream()));
                DataOutputStream out=new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
                writeFrame(out,T_HELLO,(myName+"\n"+code).getBytes("UTF-8"));
                Frame f=readFrame(in,1024);
                if(f==null || f.type!=T_HELLO) throw new IOException("Risposta HELLO non valida");
                String[] h=new String(f.data,"UTF-8").split("\n",2);
                if(h.length<2 || !code.equals(h[1])) throw new IOException("Codice non valido");
                s.setSoTimeout(0);
                Peer p=new Peer();p.socket=s;p.in=in;p.out=out;p.name=h[0];p.host=host;
                if(register(p)) { append("--> ENTRATO IN CHAT: "+p.name+" ("+host+")"); receiveLoop(p); }
                else s.close();
            } catch(Exception e) { try{s.close();}catch(Exception ignored){} }
            finally { connecting.remove(host); }
        }});
    }
    private boolean register(Peer p) {
        synchronized(peers) {
            for(Peer x:peers) if(x.host.equals(p.host)) return false;
            peers.add(p);
        }
        ui.post(new Runnable(){public void run(){refreshRecipients();}});
        return true;
    }
    private boolean hasPeer(String host) {
        synchronized(peers){for(Peer p:peers)if(p.host.equals(host))return true;} return false;
    }
    private void refreshRecipients() {
        String selected=(String)recipients.getSelectedItem();
        recipientAdapter.clear();recipientAdapter.add("Tutti i PC");
        synchronized(peers) { for(Peer p:peers) recipientAdapter.add(p.name+" ("+p.host+")"); }
        if(selected!=null) { int n=recipientAdapter.getPosition(selected); if(n>=0)recipients.setSelection(n); }
    }
    private void receiveLoop(Peer p) {
        String currentName=null, currentPath=null; FileOutputStream file=null; long expected=0, got=0;
        try {
            while(active) {
                Frame f=readFrame(p.in,70000); if(f==null)break;
                if(f.type==T_PING) continue;
                if(f.type==T_BYE) break;
                if(f.type==T_TESTO) append(p.name+": "+new String(f.data,"UTF-8"));
                else if(f.type==T_PRIV) append("[Privato da "+p.name+"]: "+new String(f.data,"UTF-8"));
                else if(f.type==T_FSTART) {
                    if(file!=null)try{file.close();}catch(Exception ignored){}
                    String[] parts=new String(f.data,"UTF-8").split("\n",2);
                    currentName=safeName(parts[0]); expected=parts.length>1?parseLong(parts[1]):0; got=0;
                    File dir=new File(receiveDir);dir.mkdirs();File dest=uniqueFile(dir,currentName);
                    currentPath=dest.getAbsolutePath();file=new FileOutputStream(dest);
                    append("Ricezione file in corso: "+currentName);
                } else if(f.type==T_FDATA && file!=null) {
                    file.write(f.data);got+=f.data.length;
                    final long g=got, ex=expected; final String nm=currentName;
                    ui.post(new Runnable(){public void run(){progress.setText("Ricezione "+nm+": "+(ex>0?(100*g/ex):0)+"%");}});
                } else if(f.type==T_FEND && file!=null) {
                    file.close();file=null;append("File RICEVUTO da "+p.name+": "+currentName+" in "+currentPath);
                    currentPath=null;
                    ui.post(new Runnable(){public void run(){progress.setText("");}});
                } else if(f.type==T_FABORT && file!=null) {
                    file.close();file=null;if(currentPath!=null)new File(currentPath).delete();currentPath=null;
                }
            }
        } catch(Exception e) { if(active) append("Connessione terminata con "+p.name); }
        finally {
            if(file!=null)try{file.close();}catch(Exception ignored){}
            if(currentPath!=null)new File(currentPath).delete();
            removePeer(p);
        }
    }
    private void removePeer(Peer p) {
        boolean removed=peers.remove(p);
        try{p.socket.close();}catch(Exception ignored){}
        if(removed){append("<-- USCITO DALLA CHAT: "+p.name);ui.post(new Runnable(){public void run(){refreshRecipients();}});}
    }
    private void sendText() {
        String txt=message.getText().toString().trim(); if(txt.length()==0)return;
        if(!active){append("Attiva prima la chat.");return;}
        String target=selectedIp(); byte type=target.length()==0?T_TESTO:T_PRIV;
        byte[] data;
        try{data=txt.getBytes("UTF-8");}catch(Exception e){return;}
        int count=0;
        synchronized(peers){for(Peer p:new ArrayList<Peer>(peers))if(target.length()==0||target.equals(p.host)){
            try{send(p,type,data);count++;}catch(Exception ex){removePeer(p);}
        }}
        append(target.length()==0?"Tu: "+txt:"Tu [Privato a "+recipients.getSelectedItem()+"]: "+txt);
        if(count==0)append("Nessun destinatario connesso.");
        message.setText("");
    }
    private String selectedIp() {
        String s=(String)recipients.getSelectedItem(); if(s==null||"Tutti i PC".equals(s))return "";
        int a=s.lastIndexOf('('), b=s.lastIndexOf(')');
        return a>=0&&b>a?s.substring(a+1,b):"";
    }
    private void send(Peer p,byte type,byte[] data)throws IOException {
        synchronized(p.writeLock){writeFrame(p.out,type,data);}
    }
    private void chooseFile() {
        // Selezione file tramite picker di sistema (ACTION_GET_CONTENT, compatibile API 15).
        Intent i=new Intent(Intent.ACTION_GET_CONTENT);i.setType("*/*");
        try{startActivityForResult(Intent.createChooser(i,"Scegli un file"),41);}
        catch(Exception e){append("Impossibile aprire il selettore file.");}
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request==41 && result==RESULT_OK && data!=null) {
            Uri uri=data.getData(); if(uri!=null) sendUri(uri);
        }
    }
    private void sendUri(final Uri uri) {
        if(!active){append("Attiva prima la chat.");return;}
        final String target=selectedIp();
        workers.execute(new Runnable(){public void run(){
            try {
                InputStream src=getContentResolver().openInputStream(uri);
                if(src==null)throw new IOException("File non leggibile");
                String name=uri.getLastPathSegment(); if(name==null||name.length()==0)name="file";
                // ACTION_GET_CONTENT non garantisce un display name: usa il nome finale del percorso.
                long total=0; ByteArrayOutputStream tmp=new ByteArrayOutputStream();
                byte[] buf=new byte[60000];int n;
                while((n=src.read(buf))>0){tmp.write(buf,0,n);total+=n;}
                src.close(); byte[] all=tmp.toByteArray();
                byte[] header=(safeName(name)+"\n"+total).getBytes("UTF-8");
                List<Peer> dest=new ArrayList<Peer>();
                synchronized(peers){for(Peer p:peers)if(target.length()==0||target.equals(p.host))dest.add(p);}
                for(Peer p:dest)send(p,T_FSTART,header);
                long sent=0;
                for(int off=0;off<all.length;off+=60000) {
                    int len=Math.min(60000,all.length-off);byte[] chunk=Arrays.copyOfRange(all,off,off+len);
                    for(Peer p:dest)send(p,T_FDATA,chunk);sent+=len;
                    final int pct=total==0?100:(int)(100*sent/total);
                    ui.post(new Runnable(){public void run(){progress.setText("Invio file: "+pct+"%");}});
                }
                for(Peer p:dest)send(p,T_FEND,new byte[0]);
                append("File inviato: "+name);
            } catch(Exception e){append("Errore invio file: "+e.getMessage());}
        }});
    }
    private void startReconnectLoop() {
        ui.postDelayed(new Runnable(){public void run(){
            if(!active)return;
            synchronized(peers){for(Peer p:new ArrayList<Peer>(peers))try{send(p,T_PING,new byte[0]);}catch(Exception e){removePeer(p);}}
            for(String host:new ArrayList<String>(saved.keySet()))connectTo(host);
            ui.postDelayed(this,6000);
        }},6000);
    }
    private void stopChat() {
        active=false;
        try{if(server!=null)server.close();}catch(Exception ignored){}
        synchronized(peers){for(Peer p:new ArrayList<Peer>(peers)){
            try{send(p,T_BYE,new byte[0]);}catch(Exception ignored){}
            try{p.socket.close();}catch(Exception ignored){}
        }peers.clear();}
        ui.post(new Runnable(){public void run(){refreshRecipients();progress.setText("");}});
        append("Disconnesso dalla chat.");
    }
    private void savePrefs() {
        getSharedPreferences("chat",MODE_PRIVATE).edit().putString("name",myName).putString("code",code)
            .putInt("port",port).putString("ips",join(saved.keySet())).commit();
    }
    private void loadPrefs() {
        android.content.SharedPreferences p=getSharedPreferences("chat",MODE_PRIVATE);
        myName=p.getString("name","Android");code=p.getString("code","1234");port=p.getInt("port",5050);
        String ips=p.getString("ips","");
        for(String x:ips.split(","))if(x.trim().length()>0)saved.put(x.trim(),x.trim());
    }
    private String join(Set<String> set) {StringBuilder b=new StringBuilder();for(String s:set){if(b.length()>0)b.append(",");b.append(s);}return b.toString();}
    private static long parseLong(String s){try{return Long.parseLong(s);}catch(Exception e){return 0;}}
    private static String safeName(String s) {
        if(s==null)s="file";s=s.replaceAll("[\\\\/:*?\"<>|]","_").trim();
        if(s.length()==0)s="file";if(s.length()>150)s=s.substring(0,150);return s;
    }
    private static File uniqueFile(File dir,String name) {
        File f=new File(dir,name);if(!f.exists())return f;
        String stem=name,ext="";int dot=name.lastIndexOf('.');
        if(dot>0){stem=name.substring(0,dot);ext=name.substring(dot);}
        for(int i=1;i<10000;i++){f=new File(dir,stem+" ("+i+")"+ext);if(!f.exists())return f;}
        return new File(dir,System.currentTimeMillis()+ext);
    }
    private static class Frame {byte type;byte[] data;Frame(byte t,byte[] d){type=t;data=d;}}
    private static void writeFrame(DataOutputStream out,byte type,byte[] data)throws IOException {
        if(data==null)data=new byte[0];int n=data.length;
        out.writeByte(type);out.writeByte(n&255);out.writeByte((n>>8)&255);out.writeByte((n>>16)&255);out.writeByte((n>>24)&255);
        if(n>0)out.write(data);out.flush();
    }
    private static Frame readFrame(DataInputStream in,int max)throws IOException {
        int type;try{type=in.readUnsignedByte();}catch(EOFException e){return null;}
        int b1=in.readUnsignedByte(),b2=in.readUnsignedByte(),b3=in.readUnsignedByte(),b4=in.readUnsignedByte();
        int n=b1|(b2<<8)|(b3<<16)|(b4<<24);
        if(n<0||n>max)throw new IOException("Frame non valido: "+n);
        byte[] data=new byte[n];in.readFully(data);return new Frame((byte)type,data);
    }
    private void openReceived() {
        File f=new File(receiveDir);f.mkdirs();
        Toast.makeText(this,"File ricevuti in: "+receiveDir,Toast.LENGTH_LONG).show();
        append("Cartella dei file ricevuti: "+receiveDir);
    }
    @Override protected void onDestroy(){stopChat();workers.shutdownNow();super.onDestroy();}
}
