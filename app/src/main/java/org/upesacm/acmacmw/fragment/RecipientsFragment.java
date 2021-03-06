package org.upesacm.acmacmw.fragment;


import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.google.firebase.database.FirebaseDatabase;

import org.upesacm.acmacmw.R;
import org.upesacm.acmacmw.adapter.PostsRecyclerViewAdapter;
import org.upesacm.acmacmw.adapter.RecepientsAdapter;
import org.upesacm.acmacmw.model.Member;
import org.upesacm.acmacmw.model.NewMember;
import org.upesacm.acmacmw.retrofit.MembershipClient;

import java.util.ArrayList;
import java.util.HashMap;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * A simple {@link Fragment} subclass.
 */
public class RecipientsFragment extends Fragment implements
        Callback<NewMember>,
        RecepientsAdapter.InteractionListener {

    public static final int NEW_MEMBER_ALREADY_PRESENT=1;
    public static final int DATA_SAVE_SUCCESSFUL=2;
    public static final int DATA_SAVE_FAILED=3;
    public static final int ALREADY_PART_OF_ACM=4;

    FirebaseDatabase database;
    RecyclerView recyclerViewRecepients;
    MembershipClient membershipClient;
    RecepientsAdapter recepientsAdapter;
    ProgressBar progressBar;
    FragmentInteractionListener listener;
    NewMember newMember;
    public RecipientsFragment() {
        // Required empty public constructor
    }


    public static RecipientsFragment newInstance(FirebaseDatabase database, MembershipClient membershipClient,
                                          NewMember newMember) {
        RecipientsFragment fragment = new RecipientsFragment();
        fragment.database=database;
        fragment.membershipClient = membershipClient;
        fragment.newMember = newMember;
        if(newMember == null)
            throw new IllegalArgumentException("newMember cannot be null");
        return fragment;
    }

    @Override
    public void onAttach(Context context) {
        if(context instanceof FragmentInteractionListener) {
            super.onAttach(context);
            listener=(FragmentInteractionListener)context;
        }
        else
            throw new IllegalStateException(context.toString()+" must implement " +
                    "FragmentInteractionListener");
    }
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_recipients, container, false);
        recyclerViewRecepients = view.findViewById(R.id.recycler_view_recepients);
        recyclerViewRecepients.setLayoutManager(new LinearLayoutManager(getContext()));

        progressBar = view.findViewById(R.id.progress_bar_recepients);

        Toast.makeText(getContext(),"fetching recipients",Toast.LENGTH_SHORT).show();
        membershipClient.getOTPRecipients()
                .enqueue(new Callback<HashMap<String, String>>() {
                    @Override
                    public void onResponse(Call<HashMap<String, String>> call, Response<HashMap<String, String>> response) {
                        System.out.println("onResonse getOTPRecipients : "+response.body());
                        HashMap<String,String> hashMap = response.body();
                        ArrayList<String> recepients=new ArrayList<>();
                        for(String key:hashMap.keySet()) {
                            recepients.add(hashMap.get(key));
                        }
                        recepientsAdapter = new RecepientsAdapter(recepients,database,RecipientsFragment.this);
                        recyclerViewRecepients.setAdapter(recepientsAdapter);

                        progressBar.setVisibility(View.INVISIBLE);
                    }

                    @Override
                    public void onFailure(Call<HashMap<String, String>> call, Throwable t) {
                        t.printStackTrace();
                        Toast.makeText(getContext(),"Failed to fetch recepients",Toast.LENGTH_SHORT).show();
                    }
                });

        progressBar.setVisibility(View.VISIBLE);
        return view;
    }

    @Override
    public void onRecepientSelect(String recipientSap) {
        NewMember completeNewMember = new NewMember.Builder()
                .setFullName(newMember.getFullName())
                .setSapId(newMember.getSapId())
                .setEmail(newMember.getEmail())
                .setBranch(newMember.getBranch())
                .setYear(newMember.getYear())
                .setPhoneNo(newMember.getPhoneNo())
                .setWhatsappNo(newMember.getWhatsappNo())
                .setPremium(newMember.isPremium())
                .setOtp(newMember.getOtp())
                .setRecipientSap(recipientSap)
                .build();

        newMember = completeNewMember;

        Call<NewMember> call = membershipClient.getNewMemberData(newMember.getSapId());
                            call.enqueue(this);
    }

    @Override
    public void onResponse(Call<NewMember> call, Response<NewMember> response) {
        NewMember nm=response.body();
        if(nm==null) {
            Call<Member> memberCall=membershipClient.getMember(newMember.getSapId());
            memberCall.enqueue(new Callback<Member>() {
                @Override
                public void onResponse(Call<Member> call, Response<Member> response) {
                    if(response.body()==null) {
                        membershipClient.saveNewMemberData(newMember.getSapId(), newMember)
                                .enqueue(new Callback<NewMember>() {
                                    @Override
                                    public void onResponse(Call<NewMember> call, Response<NewMember> response) {
                                        if(response.code()==200) {
                                            //MemberRegistrationFragment.this.saveSignUpInfoLocally();
                                            listener.onNewMemberDataSave(DATA_SAVE_SUCCESSFUL,newMember);
                                        }
                                        else {
                                            listener.onNewMemberDataSave(DATA_SAVE_FAILED, newMember);
                                        }
                                    }
                                    @Override
                                    public void onFailure(Call<NewMember> call, Throwable t) {
                                        listener.onNewMemberDataSave(DATA_SAVE_FAILED,newMember);
                                    }
                                });
                    }
                    else {
                        listener.onNewMemberDataSave(ALREADY_PART_OF_ACM,newMember);
                    }
                }

                @Override
                public void onFailure(Call<Member> call, Throwable t) {
                    Toast.makeText(RecipientsFragment.this.getContext(),"Failed",Toast.LENGTH_SHORT);
                    listener.onNewMemberDataSave(DATA_SAVE_FAILED,newMember);
                }
            });
        }
        else {
            listener.onNewMemberDataSave(NEW_MEMBER_ALREADY_PRESENT,newMember);
        }
    }

    @Override
    public void onFailure(Call<NewMember> call, Throwable t) {
        System.out.println("Failed to authenticate");
        listener.onNewMemberDataSave(DATA_SAVE_FAILED,newMember);
    }

    public interface FragmentInteractionListener {
        void onNewMemberDataSave(int resultCode,NewMember newMember);
    }
}
