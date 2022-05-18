package wro_2022_shkaff;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;

import lejos.hardware.Audio;
import lejos.hardware.BrickFinder;
import lejos.hardware.Button;
import lejos.hardware.Sound;
import lejos.hardware.Sounds;
import lejos.hardware.ev3.EV3;
import lejos.hardware.motor.EV3LargeRegulatedMotor;
import lejos.hardware.motor.EV3MediumRegulatedMotor;
import lejos.hardware.motor.NXTRegulatedMotor;
import lejos.hardware.motor.UnregulatedMotor;
import lejos.hardware.port.MotorPort;
import lejos.hardware.port.Port;
import lejos.hardware.port.SensorPort;
import lejos.hardware.sensor.EV3ColorSensor;
import lejos.hardware.sensor.EV3TouchSensor;
import lejos.hardware.sensor.EV3UltrasonicSensor;
import lejos.hardware.sensor.NXTLightSensor;
import lejos.hardware.sensor.SensorMode;
import lejos.hardware.video.Video;
import lejos.hardware.video.YUYVImage;
import lejos.robotics.SampleProvider;
import lejos.utility.Delay;

@SuppressWarnings("unused")
public class MainClass {
	static SysExit se;
	static UnregulatedMotor xMot;
	static EV3LargeRegulatedMotor yMot;
	static NXTRegulatedMotor lift;
	static EV3MediumRegulatedMotor turn;
	static UltrasonicSensor usX;
	static UltrasonicSensor usY;
	static LightSensor lX;
	static TouchSensor tY;
	static int pos = 0;
	static EV3 ev3;
	static Audio audio;
	private static int WIDTH = 160;
    private static int HEIGHT = 120;
    private static int NUM_PIXELS = WIDTH * HEIGHT;
    private static int FRAME_SIZE = NUM_PIXELS * 2;
	static Video vid;
	static byte[] frame;
	static ArrayList<Item> items;
	static boolean dir = true;
	
	public static String checkWeather(String lat, String lon) throws IOException, ParseException {
		final String api = "https://api.weather.yandex.ru/v2/informers?" + "lat=" + lat + "&" + "lon=" + lon + "&";
		URL url = new URL(api);
		HttpURLConnection connection = (HttpURLConnection)url.openConnection();
		connection.addRequestProperty("X-Yandex-API-Key", "891d954c-4dce-4b21-9230-5b7e336df6bd");
		BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream()));
         
        StringBuffer json = new StringBuffer(1024);
        String tmp="";
        while((tmp=reader.readLine())!=null)
            json.append(tmp).append("\n");
        reader.close();
         
        JSONObject data = (JSONObject)(new JSONParser().parse(json.toString()));
        JSONObject fact = (JSONObject) data.get("fact");
        String temperature = (String) fact.get("feels_like");
        String condition = (String) fact.get("condition");
        String cond = "";
        switch (condition) {
        		/*clear Ч €сно.
				partly-cloudy Ч малооблачно.
				cloudy Ч облачно с про€снени€ми.
				overcast Ч пасмурно.
				drizzle Ч морось.
				light-rain Ч небольшой дождь.
				rain Ч дождь.
				moderate-rain Ч умеренно сильный дождь.
				heavy-rain Ч сильный дождь.
				continuous-heavy-rain Ч длительный сильный дождь.
				showers Ч ливень.
				wet-snow Ч дождь со снегом.
				light-snow Ч небольшой снег.
				snow Ч снег.
				snow-showers Ч снегопад.
				hail Ч град.
				thunderstorm Ч гроза.
				thunderstorm-with-rain Ч дождь с грозой.
				thunderstorm-with-hail Ч гроза с градом.*/
        	case "clear":
        	case "partly-cloudy":
        	case "cloudy":
        		cond = "0";
        		break;
        	case "overcast":
        	case "drizzle":
        	case "light-rain":
        	case "rain":
        	case "heavy-rain":
        	case "continuous-heavy-rain":
        	case "hail":
        	case "showers":
        		cond = "1";
        		break;
        	case "thunderstorm":
        	case "thunderstorm-with-rain":
        	case "thunderstorm-with-hail":
        		cond = "2";
        		break;
        	case "snow":
        	case "snow-showers":
        		cond = "3";
        		break;
        	default:
        		cond = "0";
        	
        		
        }
        return temperature + " " + cond;
	}
	
	public static void scanItems() throws IOException {
		int i = 1;
		while (usX.distance < 120) {
			movX(i, 25, 5);
			i++;
			String qrData = decodeQRCode();
			if (qrData != null) {
				String[] item = qrData.split(" ");
				items.add(new Item(Integer.valueOf(item[0]).intValue(), Integer.valueOf(item[1]).intValue(), Float.valueOf(item[2]).floatValue(), Float.valueOf(item[3]).floatValue()));
			}
		}
		xMot.setPower(100);
		while (usX.distance > 5) {
			
			xMot.backward();
		}
		xMot.stop();
	}
		
	public static String decodeQRCode() throws IOException {
		vid.grabFrame(frame);
		BufferedImage img = new BufferedImage(WIDTH, HEIGHT,BufferedImage.TYPE_INT_RGB);
		for(int i=0;i<FRAME_SIZE;i+=4) {
            int y1 = frame[i] & 0xFF;
            int y2 = frame[i+2] & 0xFF;
            int u = frame[i+1] & 0xFF;
            int v = frame[i+3] & 0xFF;
            int rgb1 = convertYUVtoARGB(y1,u,v);
            int rgb2 = convertYUVtoARGB(y2,u,v);
            img.setRGB((i % (WIDTH * 2)) / 2, i / (WIDTH * 2), rgb1);
            img.setRGB((i % (WIDTH * 2)) / 2 + 1, i / (WIDTH * 2), rgb2);
        }
		LuminanceSource source = new BufferedImageLuminanceSource(img);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
        Result result;
		try {
			result = new MultiFormatReader().decode(bitmap);
			return result.getText();
		} catch (NotFoundException e) {
			// TODO Auto-generated catch block
			return null;
		}
	}
	
	private static int convertYUVtoARGB(int y, int u, int v) {
        int c = y - 16;
        int d = u - 128;
        int e = v - 128;
        int r = (298*c+409*e+128)/256;
        int g = (298*c-100*d-208*e+128)/256;
        int b = (298*c+516*d+128)/256;
        r = r>255? 255 : r<0 ? 0 : r;
        g = g>255? 255 : g<0 ? 0 : g;
        b = b>255? 255 : b<0 ? 0 : b;
        return 0xff000000 | (r<<16) | (g<<8) | b;
    }
	
	public static void movX(int tPos, int power, float threshold) {
		xMot.setPower(power);
		// Desired item is rightmost than pos
		if (tPos > pos) {
			dir = true;
			while (tPos > pos) {
				xMot.forward();
				if (lX.light < threshold)  {
					pos++;
					while (lX.light < threshold) {
						xMot.forward();
					}
					Delay.msDelay(200);
					audio.systemSound(2);
				}
				
			}
		} else if (tPos < pos) { // Desired item is leftmost than pos
			dir = false;
			while (tPos > pos) {
				xMot.backward();
				if (lX.light < threshold) {
					pos--;
					while (lX.light < threshold) {
						xMot.backward();
					}
					Delay.msDelay(200);
					audio.systemSound(2);
				}
			}
		} else {
			while (lX.light >=threshold) {
				if (dir) {
					xMot.forward();
				} else {
					xMot.backward();
				}
				Delay.msDelay(200);
				audio.systemSound(2);
			}
		}
		
		xMot.stop();
		 
	}
	
	public static void movY_T(int pwr) {
		yMot.setSpeed((int)(yMot.getMaxSpeed() * pwr / 100));
		while (tY.isPressed == 0){
			yMot.forward();
		}
		yMot.stop();
	}
	
	public static void get() {
		movY_T(100);
		lift.setSpeed(lift.getMaxSpeed());
		yMot.setSpeed((int) (yMot.getMaxSpeed()*0.6));
		lift.rotateTo(1200);
		lift.rotateTo(3600, true);
		while (usY.distance > 7) {
			yMot.backward();
		}
		yMot.stop();
		lift.rotateTo(0);
		turn.setSpeed((int) (turn.getMaxSpeed() * 0.1));
		turn.rotateTo(180);
	}
	
	public static void init() throws IOException {
		// panic button thread
		se = new SysExit();
		se.setDaemon(true);
		se.start();
		// init Motors
		xMot = new UnregulatedMotor(MotorPort.A);
		yMot = new EV3LargeRegulatedMotor(MotorPort.B);
		lift = new NXTRegulatedMotor(MotorPort.C);
		turn = new EV3MediumRegulatedMotor(MotorPort.D);
		// init Sensors
		// <- ->
		lX = new LightSensor(SensorPort.S1);
		usX = new UltrasonicSensor(SensorPort.S2);
		// ^ v
		usY = new UltrasonicSensor(SensorPort.S3);
		tY = new TouchSensor(SensorPort.S4);
		// make threads background
		lX.setDaemon(true);
		usX.setDaemon(true);
		usY.setDaemon(true);
		tY.setDaemon(true);
		// start sensor threads
		lX.start();
		usX.start();
		usY.start();
		tY.start();
		EV3 ev3 = (EV3) BrickFinder.getDefault();
		audio = ev3.getAudio();
		vid =  BrickFinder.getDefault().getVideo();
		vid.open(WIDTH,HEIGHT);
		frame = vid.createFrame();
		items = new ArrayList<Item>();
	}
	
	public static Item[] removeTheElement(Item[] arr, int index)
    {
 
        // If the array is empty
        // or the index is not in array range
        // return the original array
        if (arr == null || index < 0
            || index >= arr.length) {
 
            return arr;
        }
 
        // Create another array of size one less
        Item[] anotherArray = new Item[arr.length - 1];
 
        // Copy the elements except the index
        // from original array to the other array
        for (int i = 0, k = 0; i < arr.length; i++) {
 
            // if the index is
            // the removal element index
            if (i == index) {
                continue;
            }
 
            // if the index is not
            // the removal element index
            anotherArray[k++] = arr[i];
        }
 
        // return the resultant array
        return anotherArray;
    }
	
	
	
	public static void main(String[] args) throws IOException, ParseException {
		// TODO Auto-generated method stub
		init();
		audio.systemSound(0);
		scanItems();
		audio.systemSound(0);
		String[] w = checkWeather("53.4186", "59.0472").split(" ");
		float temperature = Float.valueOf(w[0]).floatValue();
		Item[] its = items.toArray(new Item[0]);
		boolean isShirt = false;
		boolean isPants = false;
		boolean isSweater = temperature > 20;
		boolean isUPants = temperature > 0;
		boolean isUmbrella = !(w[1] == "1");
		boolean isRaincoat = (w[1] == "2" || w[1] == "1");
		for(int i = 0; i < its.length; i++) {
			
			if (its[i].minT > temperature || its[i].maxT < temperature) {
				continue;
			}
			boolean isFound = false;
			if (!isShirt || !isPants || !isSweater || !isUPants || !isUmbrella || !isRaincoat) {
				if (!isShirt && its[i].iType == Item.ItemType.SHIRT) {
					isShirt = true;
					isFound = true;
				} else if (!isPants && its[i].iType == Item.ItemType.PANTS) {
					isPants = true;
					isFound = true;
				} else if (!isSweater && its[i].iType == Item.ItemType.SWEATER) {
					isSweater = true;
					isFound = true;
				} else if (!isUPants && its[i].iType == Item.ItemType.UNDERPANTS) {
					isUPants = true;
					isFound = true;
				} else if (!isUmbrella && its[i].iType == Item.ItemType.UMBRELLA) {
					isUmbrella = true;
					isRaincoat = true;
					isFound = true;
				} else if (!isRaincoat && its[i].iType == Item.ItemType.RAINCOAT) {
					isUmbrella = true;
					isRaincoat = true;
					isFound = true;
				} 
				
				if (isFound) {
					movX(i + 1, 40, 4);
					get();
					its = removeTheElement(its, i);
					movX(i+1, 40, 4);
				}
			} else {
				break;
			}
		}
	}
}

class TouchSensor extends Thread {//sensar kasaniya
	public int isPressed = 0;
	EV3TouchSensor ts = null;
	SampleProvider sp = null;
	public TouchSensor(Port p) {
		// TODO Auto-generated constructor stub
		ts = new EV3TouchSensor(p);
		sp = ts.getTouchMode();
	}
	
	public void run() {//sensar kasaniya
		// TODO Auto-generated method stub
		float[] touch = new float[sp.sampleSize()];
		while (true) {
			sp.fetchSample(touch, 0);
			isPressed = (int)touch[0];
		}
	}
}

class LightSensor extends Thread {//
	
	public float light = 100;
	// NXTLightSensor ls = null;
	EV3ColorSensor ls = null;
	SampleProvider sp = null;
	
	LightSensor(Port p){
		// ls = new NXTLightSensor(p);
		ls = new EV3ColorSensor(p);
		sp = ls.getRedMode();
	}
	public void run() {
		float[] sample = new float[sp.sampleSize()];
		while(true) {
			sp.fetchSample(sample, 0);
			light = sample[0]*100;
		}
	}
}

class UltrasonicSensor extends Thread {
	
	public float distance = 255;
	SampleProvider sp = null;
	EV3UltrasonicSensor us = null;
	
	public UltrasonicSensor(Port p) {
		// TODO Auto-generated constructor stub
		us = new EV3UltrasonicSensor(p);
		sp = us.getDistanceMode();
	}
	public void run() {
		float[] sample = new float[sp.sampleSize()];
		while (true) {
			sp.fetchSample(sample, 0);
			distance = sample[0]*100;
		}
	}
}

class SysExit extends Thread {
	SysExit() {
		
	}
	
	public void run() {
		while (Button.ESCAPE.isUp()) {
			Delay.msDelay(100);
		}
		System.exit(0);
	}
}

class Item {
	public enum ItemType {
		PANTS,
		SHIRT,
		SWEATER,
		UMBRELLA,
		RAINCOAT,
		UNDERPANTS;
		
		public static ItemType fromInteger(int x) {
			switch(x) {
				case 0:
					return PANTS;
				case 1:
					return SHIRT;
				case 2:
					return SWEATER;
				case 3:
					return UMBRELLA;
				case 4:
					return RAINCOAT;
				case 5:
					return UNDERPANTS;
			}
			return null;
			
		}
	}
	public enum WeatherType{
		DRY,
		WET,
		THUNDER,
		SNOW,
		UNI;
		public static WeatherType fromInteger(int x) {
			switch(x) {
				case 0:
					return DRY;
				case 1 : 
					return WET;
				case 2 :
					return THUNDER;
				case 3 : 
					return SNOW;
				case 4:
					return UNI;
			}
		return null;
		}
	}
	public ItemType iType;
	public WeatherType wType;
	public float minT;
	public float maxT;
	
	Item(int itype, int wtype, float tmin, float tmax){
		this.iType = ItemType.fromInteger(itype);
		this.wType = WeatherType.fromInteger(wtype);
		this.minT = tmin;
		this.maxT = tmax;
	}
	
}

