package org.polkadot.example.promise;

import com.onehilltech.promises.Promise;
import org.polkadot.api.SubmittableExtrinsic;
import org.polkadot.api.Types.QueryableStorageFunction;
import org.polkadot.api.Types.SubmittableExtrinsicFunction;
import org.polkadot.api.promise.ApiPromise;
import org.polkadot.common.keyring.Types;
import org.polkadot.example.TestingPairs;
import org.polkadot.rpc.provider.ws.WsProvider;
import org.polkadot.types.rpc.ExtrinsicStatus;
import org.polkadot.types.type.Event;
import org.polkadot.types.type.EventRecord;
import org.polkadot.utils.Utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicReference;

public class E10_UpgradeChain {

    static String ALICE = "5GrwvaEF5zXb26Fz9rcQpDWS57CtERHpNehXCPcNoHGKutQY";
    static int AMOUNT = 10000;

    //static String endPoint = "wss://poc3-rpc.polkadot.io/";
    //static String endPoint = "wss://substrate-rpc.parity.io/";
    //static String endPoint = "ws://45.76.157.229:9944/";
    static String endPoint = "ws://127.0.0.1:9944";

    static void initEndPoint(String[] args) {
        if (args != null && args.length >= 1) {
            endPoint = args[0];
            System.out.println(" connect to endpoint [" + endPoint + "]");
        } else {
            System.out.println(" connect to default endpoint [" + endPoint + "]");
        }
    }

    static {
        System.loadLibrary("jni");
        System.out.println("load ");
    }

    //-Djava.library.path=./libs
    public static void main(String[] args) throws InterruptedException {
        // Create an await for the API
        //Promise<ApiPromise> ready = ApiPromise.create();
        initEndPoint(args);

        WsProvider wsProvider = new WsProvider(endPoint);

        // Create the API and wait until ready (optional provider passed through)
        Promise<ApiPromise> ready = ApiPromise.create(wsProvider);

        /////////////////////////////////////////////////////////////
        /////////////////////////////////////////////////////////////
        /////////////////////////////////////////////////////////////
        /////////////////////////////////////////////////////////////
        AtomicReference<Types.KeyringInstance> keyringRef = new AtomicReference<>();
        AtomicReference<ApiPromise> apiRef = new AtomicReference<>();
        // Create the API and wait until ready
        ready.then(api -> {
            apiRef.set(api);

            System.out.println("============ start ==============");

            // retrieve the upgrade key from the chain state
            QueryableStorageFunction<Promise> key = api.query().section("sudo").function("key");

            return key.call();

        }).then(adminId -> {

            System.out.println("adminId : " + adminId);
            // find the actual keypair in the keyring (if this is an changed value, the key
            // needs to be added to the keyring before - this assumes we have defaults, i.e.
            // Alice as the key - and this already exists on the test keyring)
            Types.KeyringInstance keyring = TestingPairs.testKeyring();
            keyringRef.set(keyring);

            Types.KeyringPair adminPair = keyring.getPair(adminId.toString());


            // retrieve the runtime to upgrade to
            byte[] bytes = Files.readAllBytes(Paths.get("test.wasm"));
            String code = Utils.u8aToHex(bytes);


            SubmittableExtrinsicFunction setCode = apiRef.get().tx().section("consensus").function("setCode");
            //SubmittableExtrinsic proposal = setCode.call("0x" + code);
            SubmittableExtrinsic proposal = setCode.call(code);

            System.out.println("Upgrading from " + adminId + ", " + code.length() / 2 + " bytes");


            org.polkadot.api.Types.SubmittableModuleExtrinsics sudoSection = apiRef.get().tx().section("sudo");
            SubmittableExtrinsicFunction sudoFunc = sudoSection.function("sudo");
            SubmittableExtrinsic<Promise> call = sudoFunc.call(proposal);

            return call.signAndSendCb(adminPair, new SubmittableExtrinsic.StatusCb() {

                @Override
                public Object callback(SubmittableExtrinsic.SubmittableResult result) {
                    ExtrinsicStatus status = result.getStatus();
                    System.out.println("Proposal status:" + status.getType());
                    if (status.isFinalized()) {
                        System.out.println("You have just upgraded your chain");

                        System.out.println("Completed at block hash" + status.asFinalized().toHex());

                        System.out.println("Events:");

                        for (EventRecord event : result.getEvents()) {
                            Event eventEvent = event.getEvent();
                            System.out.println("\t" + event.getPhase().toString()
                                    + ": " + eventEvent.getSection() + "." + eventEvent.getMethod() + " " + eventEvent.getData().toString());
                        }
                        System.exit(0);
                    }
                    return null;
                }
            });

        }).then(result -> {
            System.out.println(" result " + result);
            return null;
        })._catch(err -> {
            err.printStackTrace();
            return null;
        });
    }

    static void readFile() throws IOException {
        System.out.println(new String(Files.readAllBytes(Paths.get("test.wasm"))));

    }
}
