package mhd3v.filteredsms;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.Toolbar;
import android.text.format.DateFormat;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.text.TextUtils;
import android.widget.Toast;

import java.util.ArrayList;

import static android.R.attr.activatedBackgroundIndicator;
import static android.R.attr.color;
import static android.R.attr.colorBackground;


/**
 * Created by Mahad on 11/27/2017.
 */

public class Tab1Fragment extends Fragment {

    ArrayList<sms> smsList = new ArrayList<>();

    customAdapter knownAdapter;
    ListView knownList;
    Tab1Fragment thisInstance;

    boolean[] selectedViews;
    String[] threadsToDelete;
    static boolean deletionMode = false;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.tab1_fragment, container, false);

        MainActivity activity = (MainActivity) getActivity();
        activity.setKnownInstance(this);

        thisInstance = this;

        knownList = view.findViewById(R.id.knownList);

        //if(smsList.isEmpty())
        smsList = activity.getKnownSms();

        selectedViews = new boolean[smsList.size()];
        threadsToDelete = new String[smsList.size()];

        knownAdapter = new customAdapter();

        activity.setKnownAdapter(knownAdapter);

        knownList.setAdapter(knownAdapter);

        setDefaultListener();


        knownList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> adapterView, View view, int position, long id) {

                deletionMode = true;

                MainActivity main = (MainActivity) getActivity();

                if(main.unknownInstance.deletionMode){  //if tab2 is in deletion mode
                    main.cancelDeletionMode(main.unknownInstance, main.cancelButtonUnfiltered);
                }


                main.setDeletionMode(thisInstance);


                if(deletionMode){

                    knownList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                        @Override
                        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                            ImageView contactPicture = view.findViewById(R.id.contactPicture);

                            if(!selectedViews[position]) {
                                contactPicture.setImageResource(R.drawable.knownsenderselected);
                                selectedViews[position] = true;
                                threadsToDelete[position] = smsList.get(position).senderName;
                            }

                            else{
                                contactPicture.setImageResource(R.drawable.knownsender);
                                selectedViews[position] = false;
                                threadsToDelete[position] = null;
                            }

                        }
                    });
                }


                return false;
            }
        });




        return view;
    }

    public void setDefaultListener() {

        knownList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                Intent intent = new Intent(getActivity(), CoversationActivity.class);
                Bundle args = new Bundle();
                args.putSerializable("messageList", smsList.get(position).messages);
                intent.putExtra("BUNDLE",args);

                intent.putExtra("sender", smsList.get(position).sender);
                intent.putExtra("senderName", smsList.get(position).senderName);
                intent.putExtra("threadId", smsList.get(position).threadId);

                intent.setAction("frag1");

                startActivity(intent);

            }
        });

    }


    public  class customAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return smsList.size();
        }

        @Override
        public Object getItem(int i) {
            return null;
        }

        @Override
        public long getItemId(int i) {
            return 0;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {

            view = getActivity().getLayoutInflater().inflate(R.layout.custom_list,null);

            TextView sender= view.findViewById(R.id.sender);

            sender.setText(smsList.get(i).senderName);

            TextView time= view.findViewById(R.id.time);
            String lastSenderMessageTime = smsList.get(i).messages.get(0).time;
            lastSenderMessageTime = convertDate(lastSenderMessageTime, "dd/MM - hh:mm aa");
            time.setText(lastSenderMessageTime);

            TextView text= view.findViewById(R.id.textbody);
            if(smsList.get(i).messages.get(0).messageBody.length() >= 50)
                text.setText(TextUtils.substring(smsList.get(i).messages.get(0).messageBody, 0, 50) + "...");
            else
                text.setText(smsList.get(i).messages.get(0).messageBody);

            ImageView contactPicture = view.findViewById(R.id.contactPicture);

            if(!selectedViews[i])
                contactPicture.setImageResource(R.drawable.knownsender);
            else
                contactPicture.setImageResource(R.drawable.knownsenderselected);

            return view;
        }

        public String convertDate(String dateInMilliseconds,String dateFormat) {
            return DateFormat.format(dateFormat, Long.parseLong(dateInMilliseconds)).toString();
        }


    }


}
