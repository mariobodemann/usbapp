package net.karmacoder.usb;

import android.app.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.content.*;
import android.hardware.usb.*;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Collection;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.*;

public class MainActivity extends Activity {
  private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";
  
  private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {

    public void onReceive(Context context, Intent intent) {
      String action = intent.getAction();
      if(ACTION_USB_PERMISSION.equals(action)) {
        synchronized(this) {
          UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

          if(intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
            if(device != null) {
              communicate(device);
            }
          } else {
            info("error", "permission denied for device " + device);
          }
        }
      }
    }
  };
  
  private UsbDevice selectedDevice;
  private UsbInterface selectedInterface;
  private UsbEndpoint selectedEndpoint;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    final Intent intent = getIntent();
    if(intent != null) {
      Object extra = getIntent().getParcelableExtra(UsbManager.EXTRA_DEVICE);

      if(extra != null) {
        UsbDevice device = (UsbDevice) extra;
        info("Device Connected!", device.getDeviceName());
      }
    }

    setContentView(R.layout.main);
    ((Button)findViewById(R.id.enumerate_button)).setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View view) {
          enumerate();
        }
      }
    );
  }

  private void enumerate() {
    UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
    
    UsbAccessory accessoryArray[] = manager.getAccessoryList();
    List<UsbAccessory> accessories = accessoryArray != null ? Arrays.asList(accessoryArray) : null;
    HashMap<String, UsbDevice> deviceMap = manager.getDeviceList();
    List<UsbDevice> devices = new ArrayList<UsbDevice>(deviceMap.values());
    select(accessories, devices);
    
  }

  private void communicate(UsbDevice device) {
    info("Communicate!", device.getDeviceName() + ", " + selectedInterface.getName() + ", " + selectedEndpoint.getAddress());
    
    UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
    UsbDeviceConnection connection = usbManager.openDevice(selectedDevice);
    
    if (!connection.claimInterface(selectedInterface, false)) {
      info("Retrying with force ...");
      if (!connection.claimInterface(selectedInterface, true)) {
        info("Could not claim interface, even with force.","Stopping attempts of claiming interface.");
        return;
      }
    }
    
    byte[] bytes = new byte[] {0};
    int result = connection.bulkTransfer(selectedEndpoint, bytes, bytes.length, 5000);
    
    info("Result", ""+result);
    connection.close();
  }

  private void info(String message) {
    info("Information", message);
  }

  private void info(String title, String message) {
    new AlertDialog.Builder(this)
      .setTitle(title)
      .setMessage(message)
      .show();
  }

  private void select(final List<UsbAccessory> accessories, final List<UsbDevice> devices) {
    final List<CharSequence> sequence = new ArrayList<>();

    final int accessoryCount = accessories != null ? accessories.size() : 0;
    final int deviceCount = devices != null ? devices.size() : 0;
    
    if(accessoryCount > 0) {
      for(final UsbAccessory accessory: accessories) {
        sequence.add(accessory.getDescription());
      }
    }
    
    if (deviceCount > 0) {
      for(final UsbDevice device: devices) {
        sequence.add(device.getDeviceName());
      }
    }
    
    if (sequence.isEmpty()) {
      sequence.add("<NOTHING FOUND>");
    }

    final CharSequence[] items = new CharSequence[sequence.size()];
    for (int i = 0; i < items.length; ++i) {
      items[i] = sequence.get(i);
    }
    
    AlertDialog.Builder builder = new AlertDialog.Builder(this)
      .setItems(items, new AlertDialog.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int index) {
          if(index >= (accessories != null ? accessories.size() : 0) + (devices != null ? devices.size() : 0)) {
            return;
          }

          if (index < accessoryCount) {
            final UsbAccessory accessory = accessories.get(index);
            info(accessory.toString());
          } else {
            final UsbDevice device = devices.get(index - accessoryCount);
            final List<UsbInterface> interfaces = new ArrayList<>(device.getInterfaceCount());
            for(int i = 0; i < device.getInterfaceCount(); ++i) {
              interfaces.add(device.getInterface(i));
            }

            selectInterface(device, interfaces);
          }
          dialog.dismiss();
        }
      });

    builder.setTitle("Connected USB");
    builder.show();
  }

  private void selectInterface(final UsbDevice device, final List<UsbInterface> list) {
    CharSequence sequence[] = new String[]{"none"};
    if(list != null && list.size() > 0) {
      sequence = new String[list.size()];

      int i = 0;
      for(final UsbInterface usbInterface : list) {
        if(usbInterface.getName() == null) {
          sequence[i++] = usbInterface.toString();            
        } else {
          sequence[i++] = usbInterface.getName();
        }
      }
    }

    AlertDialog.Builder builder = new AlertDialog.Builder(this)
      .setItems(sequence, new AlertDialog.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int index) {
          if(index >= list.size()) {
            return;
          }

          UsbInterface usbInterface = list.get(index);

          List<UsbEndpoint> endpoints = new ArrayList<>(usbInterface.getEndpointCount());
          for(int i = 0; i < usbInterface.getEndpointCount(); ++i) {
            endpoints.add(usbInterface.getEndpoint(i));
          }

          selectEndpoint("interface: " + usbInterface.getName(), endpoints, device, usbInterface);
        }
      });

    builder.setTitle(
      String.format(Locale.getDefault(),
      "%s: %02X,%02X,%02X", 
      device.getDeviceName(), 
      device.getDeviceClass(), 
      device.getDeviceSubclass(), 
      device.getDeviceId()));
    builder.show();
  }

  private void selectEndpoint(String title, final List<UsbEndpoint> list, 
    final UsbDevice device, final UsbInterface usbInterface) {
    CharSequence sequence[] = new String[]{"none"};
    if(list != null && list.size() > 0) {
      sequence = new String[list.size()];

      int i = 0;
      for(final Object o : list) {
        sequence[i++] = ((UsbEndpoint)o).getAddress() + "";
      }
    }

    AlertDialog.Builder builder = new AlertDialog.Builder(this)
      .setItems(sequence, new AlertDialog.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int index) {
          if(index >= list.size()) {
            return;
          }

          selectedEndpoint = list.get(index);
          selectedInterface = usbInterface;
          selectedDevice = device;

          UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
          PendingIntent permissionIntent = PendingIntent.getBroadcast(MainActivity.this, 0, new Intent(ACTION_USB_PERMISSION), 0);
          IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
          registerReceiver(usbReceiver, filter);   
          usbManager.requestPermission(device, permissionIntent);
        }
      });

    builder.setTitle(title);
    builder.show();
  }
}
