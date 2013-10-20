package dgz.bluetooth;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.UUID;
import java.io.*;

import dgz.bluetooth.R;
import dgz.bluetooth.Bluetooth.ServerOrCilent;
import android.R.bool;
import android.R.string;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;

public class chatActivity extends Activity implements OnItemClickListener,
		OnClickListener {
	/** Called when the activity is first created. */

	private ListView mListView;
	private ArrayList<deviceListItem> list;
	private Button sendButton;
	private Button disconnectButton;
	private EditText editMsgView;
	deviceListAdapter mAdapter;
	Context mContext;
	
	public static boolean isInitialized=false;

	/* 一些常量，代表服务器的名称 */
	public static final String PROTOCOL_SCHEME_L2CAP = "btl2cap";
	public static final String PROTOCOL_SCHEME_RFCOMM = "btspp";
	public static final String PROTOCOL_SCHEME_BT_OBEX = "btgoep";
	public static final String PROTOCOL_SCHEME_TCP_OBEX = "tcpobex";

	private BluetoothServerSocket mserverSocket = null;
	private ServerThread startServerThread = null;
	private clientThread clientConnectThread = null;
	public static BluetoothSocket socket = null;
	private BluetoothDevice device = null;
	private readThread mreadThread = null;;
	private BluetoothAdapter mBluetoothAdapter = BluetoothAdapter
			.getDefaultAdapter();
	public  MyDataSet myDataSet=(MyDataSet)getApplication();

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.chat);
		mContext = this;
		init();
	}

	private void init() {
		list = new ArrayList<deviceListItem>();
		mAdapter = new deviceListAdapter(this, list);
		mListView = (ListView) findViewById(R.id.list);
		mListView.setAdapter(mAdapter);
		mListView.setOnItemClickListener(this);
		mListView.setFastScrollEnabled(true);
		editMsgView = (EditText) findViewById(R.id.MessageText);
		editMsgView.clearFocus();

		sendButton = (Button) findViewById(R.id.btn_msg_send);
		sendButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				// TODO Auto-generated method stub
				String msgText = editMsgView.getText().toString();
				if (msgText.length() > 0) {
					sendMessageHandle(msgText);
					editMsgView.setText("");
					editMsgView.clearFocus();
					// close InputMethodManager
					InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
					imm.hideSoftInputFromWindow(editMsgView.getWindowToken(), 0);
				} else
					Toast.makeText(mContext, "发送内容不能为空！", Toast.LENGTH_SHORT)
							.show();
			}
		});

		disconnectButton = (Button) findViewById(R.id.btn_disconnect);
		disconnectButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				// TODO Auto-generated method stub
				if (Bluetooth.serviceOrCilent == ServerOrCilent.CILENT) {
					shutdownClient();
				} else if (Bluetooth.serviceOrCilent == ServerOrCilent.SERVICE) {
					shutdownServer();
				}
				Bluetooth.isOpen = false;
				Bluetooth.serviceOrCilent = ServerOrCilent.NONE;
				Toast.makeText(mContext, "已断开连接！", Toast.LENGTH_SHORT).show();
			}
		});
	}

	private Handler LinkDetectedHandler = new Handler() {
		@Override
		public synchronized void handleMessage(Message msg) {
			// Toast.makeText(mContext, (String)msg.obj,
			// Toast.LENGTH_SHORT).show();
			if (msg.what == 1) {
				list.add(new deviceListItem((String) msg.obj, true));
			} else if(msg.what==8) {
				String msgString=msg.obj.toString();
				if (socket == null) {
					//Toast.makeText(mContext, "没有连接", Toast.LENGTH_SHORT).show();
					Log.v("dgz","socket 连接已断开。。");
					return;
				}
				try {
					OutputStream os = socket.getOutputStream();
					os.write(msgString.getBytes());
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				list.add(new deviceListItem(msgString, false));
				mAdapter.notifyDataSetChanged();
				mListView.setSelection(list.size() - 1);
				
			}
			else {
				list.add(new deviceListItem((String) msg.obj, false));
			}
			mAdapter.notifyDataSetChanged();
			mListView.setSelection(list.size() - 1);
		}

	};

	@Override
	public synchronized void onPause() {
		super.onPause();
	}

	@Override
	public synchronized void onResume() {
		super.onResume();
		if (Bluetooth.isOpen) {
			Toast.makeText(mContext, "连接已经打开，可以通信。如果要再建立连接，请先断开！",
					Toast.LENGTH_SHORT).show();
			return;
		}
		if (Bluetooth.serviceOrCilent == ServerOrCilent.CILENT) {
			String address = Bluetooth.BlueToothAddress;
			if (!address.equals("null")) {
				device = mBluetoothAdapter.getRemoteDevice(address);
				clientConnectThread = new clientThread();
				clientConnectThread.start();
				Bluetooth.isOpen = true;
			} else {
				Toast.makeText(mContext, "address is null !",
						Toast.LENGTH_SHORT).show();
			}
		} else if (Bluetooth.serviceOrCilent == ServerOrCilent.SERVICE) {
			startServerThread = new ServerThread();
			startServerThread.start();
			Bluetooth.isOpen = true;
		}
	}

	class MycountTime extends CountDownTimer {

		public MycountTime(long millisInFuture, long countDownInterval) {
			super(millisInFuture, countDownInterval);
			// TODO 自动生成的构造函数存根
		}

		@Override
		public void onTick(long millisUntilFinished) {
			// TODO 自动生成的方法存根

		}

		@Override
		public void onFinish() {
			// TODO 自动生成的方法存根

		}

	}

	// 开启客户端
	private class clientThread extends Thread {
		public void run() {
			try {
				// 创建一个Socket连接：只需要服务器在注册时的UUID号
				// socket =
				// device.createRfcommSocketToServiceRecord(BluetoothProtocols.OBEX_OBJECT_PUSH_PROTOCOL_UUID);
				socket = device.createRfcommSocketToServiceRecord(UUID
						.fromString("00001101-0000-1000-8000-00805F9B34FB"));
				// 连接
				Message msg2 = new Message();
				msg2.obj = "请稍候，正在连接服务器:" + Bluetooth.BlueToothAddress;
				msg2.what = 0;
				LinkDetectedHandler.sendMessage(msg2);

				socket.connect();

				Message msg = new Message();
				msg.obj = "已经连接上服务端！可以发送信息。";
				msg.what = 0;
				LinkDetectedHandler.sendMessage(msg);
				
				Message msgHelloString = new Message();
				msgHelloString.obj = "呼叫单片机。。发送命令#h";// 呼叫单片机
				msgHelloString.what = 0;
				LinkDetectedHandler.sendMessage(msgHelloString);
				
				//sendMessageHandle("#h");
				Message msgHello=new Message();
				msgHello.obj="#h";
				msgHello.what=8;
				LinkDetectedHandler.sendMessage(msgHello);
				
				Message msgLinkC=new Message();
				msgLinkC.obj="#c";
				msgLinkC.what=8;
				
				Message msgLinkR=new Message();
				msgLinkR.obj="#r";
				msgLinkR.what=8;
				
				
				Message msgHelloWait = new Message();
				msgHelloWait.obj = "等待单片机响应呼叫命令。。。";
				msgHelloWait.what = 0;
				LinkDetectedHandler.sendMessage(msgHelloWait);

				// 启动接受数据
				 //Looper.prepare();
				mreadThread = new readThread();
				mreadThread.start();

				try {
					
					String strHelloString = null;
					//mreadThread.hMap.put("#o", "#o");
					int countN = 0;
					while (mreadThread.getLinkString("#o")) {
						//strHelloString = mreadThread.getString("#o");
						for (int i = 0; i < 3; i++) {
							while (countN < 500000) {
								countN++;
							}
							LinkDetectedHandler.sendMessage(msgHelloWait);
						}
					}
					Message msgConnectionMessage = new Message();
					msgConnectionMessage.obj = "呼叫单片机成功，发送建立连接命令#c，";// 与单片机建立连接
					msgConnectionMessage.what = 0;
					LinkDetectedHandler.sendMessage(msgConnectionMessage);
					
					//sendMessageHandle("#c");
					LinkDetectedHandler.sendMessage(msgLinkC);
					
					Message msgConnection = new Message();
					msgConnection.obj = "等待单片机响应建立连接命令。。。";
					msgConnection.what = 0;
					LinkDetectedHandler.sendMessage(msgConnection);
					
					//String strConnectionString = null;
					/*while (!strConnectionString.equalsIgnoreCase(mreadThread.getString("#k"))) {
						strConnectionString = mreadThread.getString("#k");

					}*/
					while(mreadThread.getLinkString("#k")){
						
					}
					Message msgRequestDataMessage = new Message();
					msgRequestDataMessage.obj = "建立连接成功，准备接收数据，发送命令#r";
					msgRequestDataMessage.what = 0;
					sendMessageHandle(msgRequestDataMessage.obj.toString());
					//sendMessageHandle("#r");
					
					LinkDetectedHandler.sendMessage(msgLinkR);
					Looper.loop();
					
				} catch (Exception e) {
					// TODO: handle exception
					Log.v("dgz", "连接出错。。。");
				}

			} catch (IOException e) {
				Log.e("connect", "", e);
				Message msg = new Message();
				msg.obj = "连接服务端异常！断开连接重新试一试。";
				msg.what = 0;
				LinkDetectedHandler.sendMessage(msg);
			}
		}
	};

	// 开启服务器
	private class ServerThread extends Thread {
		public void run() {

			try {
				/*
				 * 创建一个蓝牙服务器 参数分别：服务器名称、UUID
				 */
				mserverSocket = mBluetoothAdapter
						.listenUsingRfcommWithServiceRecord(
								PROTOCOL_SCHEME_RFCOMM,
								UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));

				Log.d("server", "wait cilent connect...");

				Message msg = new Message();
				msg.obj = "请稍候，正在等待客户端的连接...";
				msg.what = 0;
				LinkDetectedHandler.sendMessage(msg);

				/* 接受客户端的连接请求 */
				socket = mserverSocket.accept();
				Log.d("server", "accept success !");

				Message msg2 = new Message();
				String info = "客户端已经连接上！可以发送信息。";
				msg2.obj = info;
				msg.what = 0;
				LinkDetectedHandler.sendMessage(msg2);

				
				// 启动接受数据
				mreadThread = new readThread();
				mreadThread.start();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	};

	/* 停止服务器 */
	private void shutdownServer() {
		new Thread() {
			public void run() {
				if (startServerThread != null) {
					startServerThread.interrupt();
					startServerThread = null;
				}
				if (mreadThread != null) {
					mreadThread.interrupt();
					mreadThread = null;
				}
				try {
					if (socket != null) {
						socket.close();
						socket = null;
					}
					if (mserverSocket != null) {
						mserverSocket.close();/* 关闭服务器 */
						mserverSocket = null;
					}
				} catch (IOException e) {
					Log.e("server", "mserverSocket.close()", e);
				}
			};
		}.start();
	}

	/* 停止客户端连接 */
	private void shutdownClient() {
		new Thread() {
			public void run() {
				if (clientConnectThread != null) {
					clientConnectThread.interrupt();
					clientConnectThread = null;
				}
				if (mreadThread != null) {
					mreadThread.interrupt();
					mreadThread = null;
				}
				if (socket != null) {
					try {
						socket.close();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					socket = null;
				}
			};
		}.start();
	}

	// 发送数据
	public synchronized void sendMessageHandle(String msg) {
		if (socket == null) {
			Toast.makeText(mContext, "没有连接", Toast.LENGTH_SHORT).show();
			return;
		}
		try {
			OutputStream os = socket.getOutputStream();
			os.write(msg.getBytes());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		list.add(new deviceListItem(msg, false));
		mAdapter.notifyDataSetChanged();
		mListView.setSelection(list.size() - 1);
	}

	// 读取数据
	private class readThread extends Thread {
		Hashtable<String, String> hMap = new Hashtable<String, String>();
		Iterator<String> iteratorHMap = hMap.keySet().iterator();
		ArrayList<String> listStrings=new ArrayList<String>();
		
		public boolean getLinkString(String string){
			return listStrings.contains(string);
		}

		public readThread() {
			// TODO 自动生成的构造函数存根
		}

		// @SuppressWarnings("unused")
		public String getString(String str) {

			return hMap.get(str);
		}

		public String getDataString() {
			while (iteratorHMap.hasNext()) {
				return iteratorHMap.next();
			}
			return null;
		}

		public void delElement(String str) {
			if (str == iteratorHMap.next())
				iteratorHMap.remove();
		}

		public void run() {

			byte[] buffer = new byte[1024];
			int bytes;
			InputStream mmInStream = null;

			try {
				mmInStream = socket.getInputStream();
				InputStreamReader input = new InputStreamReader(mmInStream);
				BufferedReader reader = new BufferedReader(input);

				String s;
				while ((s = reader.readLine()) != null) {

					
					if (s.indexOf("@s") == -1) {
						//hMap.put(s, s);
						listStrings.add(s);
						/*Message msg = new Message();
						msg.obj = s;
						msg.what = 1;
						LinkDetectedHandler.sendMessage(msg);*/

					} else {
						Message msg = new Message();
						msg.obj = s;
						msg.what = 1;
						LinkDetectedHandler.sendMessage(msg);
						
						//myDataSet.setDataString(s);
						if(!isInitialized){
							Intent intent=new Intent(chatActivity.this,dataViewActivity.class);
							startActivity(intent);
							//Bluetooth.mTabHost.setCurrentTab(2);
							//dataViewActivity.Two.obtainMessage(2, s).sendToTarget();
						}else {
							dataViewActivity.Two.obtainMessage(2, s).sendToTarget();
						}
						
						
					}

					
				}
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		
			 catch (Exception e) {
				// TODO 自动生成的 catch 块
				e.printStackTrace();
			}
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();

		if (Bluetooth.serviceOrCilent == ServerOrCilent.CILENT) {
			shutdownClient();
		} else if (Bluetooth.serviceOrCilent == ServerOrCilent.SERVICE) {
			shutdownServer();
		}
		Bluetooth.isOpen = false;
		Bluetooth.serviceOrCilent = ServerOrCilent.NONE;
	}

	public class SiriListItem {
		String message;
		boolean isSiri;

		public SiriListItem(String msg, boolean siri) {
			message = msg;
			isSiri = siri;
		}
	}

	@Override
	public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
		// TODO Auto-generated method stub
	}

	@Override
	public void onClick(View arg0) {
		// TODO Auto-generated method stub
	}

	public class deviceListItem {
		String message;
		boolean isSiri;

		public deviceListItem(String msg, boolean siri) {
			message = msg;
			isSiri = siri;
		}
	}
}