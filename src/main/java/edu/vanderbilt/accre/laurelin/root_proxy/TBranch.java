package edu.vanderbilt.accre.laurelin.root_proxy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.vanderbilt.accre.ArrayBuilder;
import edu.vanderbilt.accre.laurelin.array.RawArray;

public class TBranch {
    protected Proxy data;
    protected ArrayList<TBranch> branches;
    private ArrayList<TLeaf> leaves;
    private ArrayList<TBasket> baskets;

    protected boolean isBranch;
    protected TBranch parent;
    protected TTree tree;

    public static class ArrayDescriptor {
        private boolean isFixed;
        private int fixedLength;
        private String branchName;
        public static ArrayDescriptor newNumArray(String mag) {
            ArrayDescriptor ret = new ArrayDescriptor();
            ret.isFixed = true;
            ret.fixedLength = Integer.parseInt(mag);
            return ret;
        }
        public static ArrayDescriptor newVarArray(String mag) {
            ArrayDescriptor ret = new ArrayDescriptor();
            ret.isFixed = false;
            ret.branchName = mag;
            return ret;
        }
        public boolean isFixed() {
            return isFixed;
        }
        public int getFixedLength() {
            return fixedLength;
        }
    }

    public TBranch(Proxy data, TTree tree, TBranch parent) {
        this.data = data;
        this.parent = parent;
        this.tree = tree;

        branches = new ArrayList<TBranch>();
        if (getClass().equals(TBranch.class)) {
            baskets = new ArrayList<TBasket>();
            leaves = new ArrayList<TLeaf>();
            isBranch = true;
            ProxyArray fBranches = (ProxyArray) data.getProxy("fBranches");
            for (Proxy val: fBranches) {
                TBranch branch = new TBranch(val, tree, this);
                // Drop branches with neither subbranches nor leaves
                if (branch.getBranches().size() != 0 || branch.getLeaves().size() != 0) {
                    branches.add(branch);
                }
            }
            ProxyArray fLeaves = (ProxyArray) data.getProxy("fLeaves");
            for (Proxy val: fLeaves) {
                TLeaf leaf = new TLeaf(val, tree, this);
                if (leaf.typeUnhandled()) {
                    continue;
                }
                leaves.add(leaf);
            }

            /*
             * Instead of being in the ObjArray of fBaskets, ROOT stores the baskets in separate
             * toplevel entries in the file
             *     Int_t      *fBasketBytes;      ///<[fMaxBaskets] Length of baskets on file
             *     Long64_t   *fBasketEntry;      ///<[fMaxBaskets] Table of first entry in each basket
             *     Long64_t   *fBasketSeek;       ///<[fMaxBaskets] Addresses of baskets on file
             */
            int fMaxBaskets = (int) data.getScalar("fMaxBaskets").getVal();
            int[] fBasketBytes = (int[]) data.getScalar("fBasketBytes").getVal();
            long[] fBasketEntry = (long[]) data.getScalar("fBasketEntry").getVal();
            long[] fBasketSeek = (long[]) data.getScalar("fBasketSeek").getVal();
            baskets = new ArrayList<TBasket>();
            TFile backing = tree.getBackingFile();
            for (int i = 0; i < fMaxBaskets; i += 1) {
                Cursor c;
                if (fBasketSeek[i] == 0) {
                    // An empty basket?
                    continue;
                }
                try {
                    c = backing.getCursorAt(fBasketSeek[i]);
                    TBasket b = TBasket.getFromFile(c, fBasketBytes[i], fBasketEntry[i], fBasketSeek[i]);
                    baskets.add(b);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        } else {
            isBranch = false;
        }
    }

    public String getTitle() {
        return (String) data.getScalar("fTitle").getVal();
    }

    public String getName() {
        return (String) data.getScalar("fName").getVal();
    }

    public String getClassName() {
        return data.getClassName();
    }

    public List<TBranch> getBranches() {
        return branches;
    }

    public List<TLeaf> getLeaves() {
        return leaves;
    }

    public List<TBasket> getBaskets() {
        return baskets;
    }

    /*
     * Note: this will all have to be refactored to support multidimensional
     *       arrays, but sufficient is today for its own troubles
     */

    // really wish java had a non-escaped string specifier
    // I wanna grab everything out of the square brackets
    //                                          \[(\d+)\]
    Pattern arrayNumPattern = Pattern.compile("\\[(\\d+)\\]");
    //                                          \[([^\]]+)\]
    Pattern arrayVarPattern = Pattern.compile("\\[([^\\]]+)\\]");

    /**
     * Returns an ArrayDescriptor describing this branch
     *
     * @return ArrayDescriptor containing the array params if this is an array
     *         null otherwise
     */
    public ArrayDescriptor getArrayDescriptor() {
        if (getLeaves().size() != 1) {
            throw new RuntimeException("Non-split branches are not supported");
        }
        TLeaf leaf = getLeaves().get(0);
        String title = leaf.getTitle();
        if (!title.contains("[")) {
            // no square brackets means no possibility of being an array
            return null;
        } else if (title.indexOf("[") != title.lastIndexOf(("["))) {
           throw new RuntimeException("Multidimensional arrays are not supported");
        } else {
            Matcher numMatcher = arrayNumPattern.matcher(title);
            Matcher varMatcher = arrayVarPattern.matcher(title);
            if (numMatcher.find()) {
                return ArrayDescriptor.newNumArray(numMatcher.group(1));
            } else if (varMatcher.find()) {
                return ArrayDescriptor.newVarArray(varMatcher.group(1));
            } else {
                throw new RuntimeException("Unable to parse array indices");
            }
        }
    }



    /**
     * Glue callback to integrate with edu.vanderbilt.accre.laurelin.array
     *
     * @return GetBasket object used by array
     */
    public ArrayBuilder.GetBasket getArrayBranchCallback() {
        ArrayBuilder.GetBasket getbasket = new ArrayBuilder.GetBasket() {
            @Override
            public ArrayBuilder.BasketKey basketkey(int basketid) {
                TBasket basket = baskets.get(basketid);
                return new ArrayBuilder.BasketKey(basket.getKeyLen(), basket.getLast(), basket.getObjLen());
            }
            @Override
            public RawArray dataWithoutKey(int basketid) {
                TBasket basket = baskets.get(basketid);
                try {
                    return new RawArray(basket.getPayload());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };
        return getbasket;
    }

    public boolean typeUnhandled() { return false; }

    public SimpleType getSimpleType() { return null; }

    public long[] getBasketEntryOffsets() {
        int basketCount = baskets.size();
        // The array processing code wants a final entry to cap the last true
        // basket from above
        long []ret = new long[basketCount + 1];
        for (int i = 0; i < basketCount; i += 1) {
            ret[i] = baskets.get(i).getBasketEntry();
        }
        ret[basketCount] = baskets.get(basketCount - 1).getLast();
        return ret;
    }
}
