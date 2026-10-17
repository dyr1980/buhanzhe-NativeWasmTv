"""Keep media/network errors distinct from a framework media-service crash."""
import os, subprocess, tempfile
from pathlib import Path
root=Path(__file__).resolve().parent.parent
bin=Path(os.environ.get('JAVA_HOME',r'C:/Users/Administrator/.jdks/corretto-1.8.0_492'))/'bin'
source='''package xiao.bu.tv;
class ErrorCheck {public static void main(String[] args){
 int[] codes={-1007,-1010,-1004,-110,-2147483648};
 String[] expected={"无法解析媒体","不支持此媒体格式","读取媒体失败","读取超时","发生错误"};
 for(int n=0;n<codes.length;n++) {
  String message=SystemPlaybackError.message(1,codes[n]);
  if(!message.contains(expected[n])||!message.contains("1/"+codes[n])||message.contains("崩溃"))throw new AssertionError(message);
 }
 if(!SystemPlaybackError.crashed(100)||SystemPlaybackError.crashed(1)||!SystemPlaybackError.message(100,0).contains("服务崩溃"))throw new AssertionError();
 System.out.println("PASS media-service crash, malformed media, unsupported codec, IO, timeout and unknown codes");
}}'''
with tempfile.TemporaryDirectory(prefix='ntv-system-error-') as folder:
 p=Path(folder)/'ErrorCheck.java';p.write_text(source,encoding='utf-8')
 subprocess.run([str(bin/'javac.exe'),'-encoding','UTF-8','-d',folder,str(root/'app/src/main/java/xiao/bu/tv/SystemPlaybackError.java'),str(p)],check=True)
 subprocess.run([str(bin/'java.exe'),'-cp',folder,'xiao.bu.tv.ErrorCheck'],check=True)
