package name.abuchen.portfolio.ui.views;

import java.util.Collections;
import java.util.List;

import name.abuchen.portfolio.model.Account;
import name.abuchen.portfolio.model.Portfolio;
import name.abuchen.portfolio.model.PortfolioTransaction;
import name.abuchen.portfolio.model.PortfolioTransaction.Type;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.model.Values;
import name.abuchen.portfolio.snapshot.PortfolioSnapshot;
import name.abuchen.portfolio.ui.Messages;
import name.abuchen.portfolio.ui.PortfolioPlugin;
import name.abuchen.portfolio.ui.dialogs.BuySellSecurityDialog;
import name.abuchen.portfolio.ui.dialogs.SecurityDeliveryDialog;
import name.abuchen.portfolio.ui.dialogs.SecurityTransferDialog;
import name.abuchen.portfolio.ui.dialogs.TransferDialog;
import name.abuchen.portfolio.ui.util.CellEditorFactory;
import name.abuchen.portfolio.ui.util.ColumnViewerSorter;
import name.abuchen.portfolio.ui.util.SharesLabelProvider;
import name.abuchen.portfolio.ui.util.ShowHideColumnHelper;
import name.abuchen.portfolio.ui.util.ShowHideColumnHelper.Column;
import name.abuchen.portfolio.ui.util.SimpleListContentProvider;
import name.abuchen.portfolio.ui.util.ViewerHelper;
import name.abuchen.portfolio.util.Dates;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.ToolBar;

public class PortfolioListView extends AbstractListView
{
    private TableViewer portfolios;
    private TableViewer transactions;
    private StatementOfAssetsViewer statementOfAssets;

    @Override
    protected String getTitle()
    {
        return Messages.LabelPortfolios;
    }

    @Override
    public void notifyModelUpdated()
    {
        portfolios.setSelection(portfolios.getSelection());
    }

    @Override
    protected void addButtons(ToolBar toolBar)
    {
        Action createPortfolio = new Action()
        {
            @Override
            public void run()
            {
                Portfolio portfolio = new Portfolio();
                portfolio.setName(Messages.LabelNoName);

                getClient().addPortfolio(portfolio);
                markDirty();

                portfolios.setInput(getClient().getPortfolios());
                portfolios.editElement(portfolio, 0);
            }
        };
        createPortfolio.setImageDescriptor(PortfolioPlugin.descriptor(PortfolioPlugin.IMG_PLUS));
        createPortfolio.setToolTipText(Messages.PortfolioMenuAdd);

        new ActionContributionItem(createPortfolio).fill(toolBar, -1);
    }

    // //////////////////////////////////////////////////////////////
    // top table: accounts
    // //////////////////////////////////////////////////////////////

    protected void createTopTable(Composite parent)
    {
        Composite container = new Composite(parent, SWT.NONE);
        TableColumnLayout layout = new TableColumnLayout();
        container.setLayout(layout);

        portfolios = new TableViewer(container, SWT.FULL_SELECTION);

        ShowHideColumnHelper support = new ShowHideColumnHelper(PortfolioListView.class.getSimpleName() + "@top", //$NON-NLS-1$
                        portfolios, layout);

        Column column = new Column(Messages.ColumnPortfolio, SWT.None, 100);
        column.setLabelProvider(new ColumnLabelProvider()
        {
            @Override
            public String getText(Object e)
            {
                return ((Portfolio) e).getName();
            }

            @Override
            public Image getImage(Object element)
            {
                return PortfolioPlugin.image(PortfolioPlugin.IMG_PORTFOLIO);
            }
        });
        column.setSorter(ColumnViewerSorter.create(Portfolio.class, "name"), SWT.DOWN); //$NON-NLS-1$
        column.setMoveable(false);
        support.addColumn(column);

        column = new Column(Messages.ColumnReferenceAccount, SWT.None, 160);
        column.setLabelProvider(new ColumnLabelProvider()
        {
            @Override
            public String getText(Object e)
            {
                Portfolio p = (Portfolio) e;
                return p.getReferenceAccount() != null ? p.getReferenceAccount().getName() : null;
            }
        });
        column.setSorter(ColumnViewerSorter.create(Portfolio.class, "referenceAccount")); //$NON-NLS-1$
        column.setMoveable(false);
        support.addColumn(column);

        support.createColumns();

        portfolios.getTable().setHeaderVisible(true);
        portfolios.getTable().setLinesVisible(true);

        portfolios.setContentProvider(new SimpleListContentProvider());
        portfolios.setInput(getClient().getPortfolios());
        portfolios.refresh();
        ViewerHelper.pack(portfolios);

        portfolios.addSelectionChangedListener(new ISelectionChangedListener()
        {
            public void selectionChanged(SelectionChangedEvent event)
            {
                Portfolio portfolio = (Portfolio) ((IStructuredSelection) event.getSelection()).getFirstElement();
                transactions.setData(Portfolio.class.toString(), portfolio);

                if (portfolio != null)
                {
                    transactions.setInput(portfolio.getTransactions());
                    transactions.refresh();
                    statementOfAssets.setInput(PortfolioSnapshot.create(portfolio, Dates.today()));
                }
                else
                {
                    transactions.setInput(null);
                    transactions.refresh();
                    statementOfAssets.setInput((PortfolioSnapshot) null);
                }
            }
        });

        new CellEditorFactory(portfolios, Portfolio.class) //
                        .notify(new CellEditorFactory.ModificationListener()
                        {
                            public void onModified(Object element, String property)
                            {
                                markDirty();
                                portfolios.refresh(transactions.getData(Account.class.toString()));
                            }
                        }) //
                        .editable("name") // //$NON-NLS-1$
                        .combobox("referenceAccount", getClient().getAccounts()) // //$NON-NLS-1$
                        .apply();

        hookContextMenu(portfolios.getTable(), new IMenuListener()
        {
            public void menuAboutToShow(IMenuManager manager)
            {
                fillPortfolioContextMenu(manager);
            }
        });
    }

    private void fillPortfolioContextMenu(IMenuManager manager)
    {
        final Portfolio portfolio = (Portfolio) ((IStructuredSelection) portfolios.getSelection()).getFirstElement();
        if (portfolio == null)
            return;

        manager.add(new Action(Messages.PortfolioMenuDelete)
        {
            @Override
            public void run()
            {
                getClient().getPortfolios().remove(portfolio);
                markDirty();

                portfolios.setInput(getClient().getPortfolios());
            }
        });
    }

    // //////////////////////////////////////////////////////////////
    // bottom table: transactions
    // //////////////////////////////////////////////////////////////

    protected void createBottomTable(Composite parent)
    {
        CTabFolder folder = new CTabFolder(parent, SWT.BORDER);

        CTabItem item = new CTabItem(folder, SWT.NONE);
        item.setText(Messages.LabelStatementOfAssets);
        statementOfAssets = new StatementOfAssetsViewer(folder, getClient());
        item.setControl(statementOfAssets.getControl());

        hookContextMenu(statementOfAssets.getTableViewer().getControl(), new IMenuListener()
        {
            public void menuAboutToShow(IMenuManager manager)
            {
                statementOfAssets.hookMenuListener(manager, PortfolioListView.this);
            }
        });

        Control control = createTransactionsTable(folder);
        item = new CTabItem(folder, SWT.NONE);
        item.setText(Messages.TabTransactions);
        item.setControl(control);

        folder.setSelection(0);

        if (!getClient().getPortfolios().isEmpty())
            portfolios.setSelection(new StructuredSelection(portfolios.getElementAt(0)), true);

        statementOfAssets.pack();
        ViewerHelper.pack(transactions);
    }

    private Control createTransactionsTable(CTabFolder folder)
    {
        Composite container = new Composite(folder, SWT.NONE);
        TableColumnLayout layout = new TableColumnLayout();
        container.setLayout(layout);

        transactions = new TableViewer(container, SWT.FULL_SELECTION);

        ShowHideColumnHelper support = new ShowHideColumnHelper(PortfolioListView.class.getSimpleName() + "@bottom", //$NON-NLS-1$
                        transactions, layout);

        Column column = new Column(Messages.ColumnDate, SWT.None, 80);
        column.setLabelProvider(new ColumnLabelProvider()
        {
            @Override
            public String getText(Object element)
            {
                return Values.Date.format(((PortfolioTransaction) element).getDate());
            }

            @Override
            public Color getForeground(Object element)
            {
                return colorFor((PortfolioTransaction) element);
            }
        });
        column.setSorter(ColumnViewerSorter.create(PortfolioTransaction.class, "date"), SWT.DOWN); //$NON-NLS-1$
        column.setMoveable(false);
        support.addColumn(column);

        column = new Column(Messages.ColumnTransactionType, SWT.None, 80);
        column.setLabelProvider(new ColumnLabelProvider()
        {
            @Override
            public String getText(Object element)
            {
                return ((PortfolioTransaction) element).getType().toString();
            }

            @Override
            public Color getForeground(Object element)
            {
                return colorFor((PortfolioTransaction) element);
            }
        });
        column.setSorter(ColumnViewerSorter.create(PortfolioTransaction.class, "type")); //$NON-NLS-1$
        column.setMoveable(false);
        support.addColumn(column);

        column = new Column(Messages.ColumnSecurity, SWT.None, 250);
        column.setLabelProvider(new ColumnLabelProvider()
        {
            @Override
            public String getText(Object element)
            {
                PortfolioTransaction t = (PortfolioTransaction) element;
                return t.getSecurity() != null ? String.valueOf(t.getSecurity()) : null;
            }

            @Override
            public Color getForeground(Object element)
            {
                return colorFor((PortfolioTransaction) element);
            }
        });
        column.setSorter(ColumnViewerSorter.create(PortfolioTransaction.class, "security")); //$NON-NLS-1$
        column.setMoveable(false);
        support.addColumn(column);

        column = new Column(Messages.ColumnShares, SWT.RIGHT, 80);
        column.setLabelProvider(new SharesLabelProvider()
        {
            @Override
            public Long getValue(Object element)
            {
                return ((PortfolioTransaction) element).getShares();
            }

            @Override
            public Color getForeground(Object element)
            {
                return colorFor((PortfolioTransaction) element);
            }
        });
        column.setSorter(ColumnViewerSorter.create(PortfolioTransaction.class, "shares")); //$NON-NLS-1$
        column.setMoveable(false);
        support.addColumn(column);

        column = new Column(Messages.ColumnAmount, SWT.RIGHT, 80);
        column.setLabelProvider(new ColumnLabelProvider()
        {
            @Override
            public String getText(Object element)
            {
                return Values.Amount.format(((PortfolioTransaction) element).getAmount());
            }

            @Override
            public Color getForeground(Object element)
            {
                return colorFor((PortfolioTransaction) element);
            }
        });
        column.setSorter(ColumnViewerSorter.create(PortfolioTransaction.class, "amount")); //$NON-NLS-1$
        column.setMoveable(false);
        support.addColumn(column);

        column = new Column(Messages.ColumnFees, SWT.RIGHT, 80);
        column.setLabelProvider(new ColumnLabelProvider()
        {
            @Override
            public String getText(Object element)
            {
                return Values.Amount.format(((PortfolioTransaction) element).getFees());
            }

            @Override
            public Color getForeground(Object element)
            {
                return colorFor((PortfolioTransaction) element);
            }
        });
        column.setSorter(ColumnViewerSorter.create(PortfolioTransaction.class, "fees")); //$NON-NLS-1$
        column.setMoveable(false);
        support.addColumn(column);

        column = new Column(Messages.ColumnPurchasePrice, SWT.RIGHT, 80);
        column.setLabelProvider(new ColumnLabelProvider()
        {
            @Override
            public String getText(Object element)
            {
                PortfolioTransaction t = (PortfolioTransaction) element;
                return t.getShares() != 0 ? Values.Amount.format(t.getActualPurchasePrice()) : null;
            }

            @Override
            public Color getForeground(Object element)
            {
                return colorFor((PortfolioTransaction) element);
            }
        });
        column.setSorter(ColumnViewerSorter.create(PortfolioTransaction.class, "actualPurchasePrice")); //$NON-NLS-1$
        column.setMoveable(false);
        support.addColumn(column);

        support.createColumns();

        transactions.getTable().setHeaderVisible(true);
        transactions.getTable().setLinesVisible(true);

        transactions.setContentProvider(new SimpleListContentProvider());

        List<Security> securities = getClient().getSecurities();
        Collections.sort(securities, new Security.ByName());

        new CellEditorFactory(transactions, PortfolioTransaction.class) //
                        .notify(new CellEditorFactory.ModificationListener()
                        {
                            public void onModified(Object element, String property)
                            {
                                PortfolioTransaction t = (PortfolioTransaction) element;
                                if (t.getCrossEntry() != null)
                                    t.getCrossEntry().updateFrom(t);

                                markDirty();
                                Portfolio portfolio = (Portfolio) transactions.getData(Portfolio.class.toString());
                                portfolios.refresh(portfolio);
                                transactions.refresh(element);

                                statementOfAssets.setInput(PortfolioSnapshot.create(portfolio, Dates.today()));
                            }
                        }) //
                        .editable("date") // //$NON-NLS-1$
                        .readonly("type") // //$NON-NLS-1$
                        .combobox("security", securities) // //$NON-NLS-1$
                        .shares("shares") // //$NON-NLS-1$
                        .amount("amount") // //$NON-NLS-1$
                        .amount("fees") // //$NON-NLS-1$
                        .apply();

        hookContextMenu(transactions.getTable(), new IMenuListener()
        {
            public void menuAboutToShow(IMenuManager manager)
            {
                fillTransactionsContextMenu(manager);
            }
        });

        return container;
    }

    private abstract class AbstractDialogAction extends Action
    {
        public AbstractDialogAction(String text)
        {
            super(text);
        }

        @Override
        public final void run()
        {
            Portfolio portfolio = (Portfolio) transactions.getData(Portfolio.class.toString());
            if (portfolio == null)
                return;

            Dialog dialog = createDialog(portfolio);
            if (dialog.open() == TransferDialog.OK)
            {
                markDirty();

                portfolios.refresh(transactions.getData(Portfolio.class.toString()));
                transactions.setInput(portfolio.getTransactions());
                statementOfAssets.setInput(PortfolioSnapshot.create(portfolio, Dates.today()));
            }
        }

        abstract Dialog createDialog(Portfolio portfolio);
    }

    private void fillTransactionsContextMenu(IMenuManager manager)
    {
        if (transactions.getData(Portfolio.class.toString()) == null)
            return;

        if (!getClient().getSecurities().isEmpty())
        {
            manager.add(new AbstractDialogAction(Messages.SecurityMenuBuy)
            {
                @Override
                Dialog createDialog(Portfolio portfolio)
                {
                    return new BuySellSecurityDialog(getClientEditor().getSite().getShell(), getClient(), portfolio,
                                    null, PortfolioTransaction.Type.BUY);
                }
            });

            manager.add(new AbstractDialogAction(Messages.SecurityMenuSell)
            {
                @Override
                Dialog createDialog(Portfolio portfolio)
                {
                    return new BuySellSecurityDialog(getClientEditor().getSite().getShell(), getClient(), portfolio,
                                    null, PortfolioTransaction.Type.SELL);
                }
            });

            if (getClient().getPortfolios().size() > 1)
            {
                manager.add(new Separator());
                manager.add(new AbstractDialogAction(Messages.SecurityMenuTransfer)
                {
                    @Override
                    Dialog createDialog(Portfolio portfolio)
                    {
                        return new SecurityTransferDialog(getClientEditor().getSite().getShell(), getClient(),
                                        portfolio);
                    }
                });
            }

            manager.add(new Separator());
            manager.add(new AbstractDialogAction(PortfolioTransaction.Type.DELIVERY_INBOUND.toString() + "...") //$NON-NLS-1$
            {
                @Override
                Dialog createDialog(Portfolio portfolio)
                {
                    return new SecurityDeliveryDialog(getClientEditor().getSite().getShell(), getClient(), portfolio,
                                    PortfolioTransaction.Type.DELIVERY_INBOUND);
                }
            });

            manager.add(new AbstractDialogAction(PortfolioTransaction.Type.DELIVERY_OUTBOUND.toString() + "...") //$NON-NLS-1$
            {
                @Override
                Dialog createDialog(Portfolio portfolio)
                {
                    return new SecurityDeliveryDialog(getClientEditor().getSite().getShell(), getClient(), portfolio,
                                    PortfolioTransaction.Type.DELIVERY_OUTBOUND);
                }
            });
        }

        boolean hasTransactionSelected = ((IStructuredSelection) transactions.getSelection()).getFirstElement() != null;
        if (hasTransactionSelected)
        {
            manager.add(new Separator());
            manager.add(new Action(Messages.MenuTransactionDelete)
            {
                @Override
                public void run()
                {
                    PortfolioTransaction transaction = (PortfolioTransaction) ((IStructuredSelection) transactions
                                    .getSelection()).getFirstElement();
                    Portfolio portfolio = (Portfolio) transactions.getData(Portfolio.class.toString());

                    if (transaction == null || portfolio == null)
                        return;

                    if (transaction.getCrossEntry() != null)
                        transaction.getCrossEntry().delete();
                    else
                        portfolio.getTransactions().remove(transaction);
                    markDirty();

                    portfolios.refresh(transactions.getData(Portfolio.class.toString()));
                    transactions.setInput(portfolio.getTransactions());
                    transactions.setSelection(new StructuredSelection(transaction), true);

                    statementOfAssets.setInput(PortfolioSnapshot.create(portfolio, Dates.today()));
                }
            });
        }
    }

    private Color colorFor(PortfolioTransaction t)
    {
        if (t.getType() == Type.SELL || t.getType() == Type.TRANSFER_OUT)
            return Display.getCurrent().getSystemColor(SWT.COLOR_DARK_RED);
        else
            return Display.getCurrent().getSystemColor(SWT.COLOR_DARK_GREEN);
    }
}
