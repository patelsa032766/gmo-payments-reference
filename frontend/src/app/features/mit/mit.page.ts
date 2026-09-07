import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { CheckoutApiService, PaymentSubmission } from '../../core/api/checkout-api.service';
import { OperationsApiService, PaymentInstrument } from '../../core/api/operations-api.service';

/** Operator workspace for reusable methods and asynchronous Koza debits. */
@Component({ changeDetection: ChangeDetectionStrategy.OnPush, imports:[FormsModule], selector: 'app-mit-page', styleUrl: './mit.page.scss', templateUrl: './mit.page.html' })
export class MitPage implements OnInit {
  private readonly api=inject(OperationsApiService);
  private readonly checkoutApi=inject(CheckoutApiService);
  protected readonly section=signal<'individual'|'koza'>('individual');
  protected readonly kozaMode=signal<'api'|'batch'>('api');
  protected readonly instruments=signal<PaymentInstrument[]>([]);
  protected readonly selectedId=signal('');
  protected readonly result=signal<PaymentSubmission|null>(null);
  protected readonly message=signal<string|null>(null);
  protected readonly submitting=signal(false);
  protected readonly operatorTokenRequired=signal(true);
  protected readonly liveCallsEnabled=signal(false);
  protected amount=10000;
  protected execution='CAPTURE';
  protected reference=`SUPPORT-${new Date().toISOString().slice(0,10).replaceAll('-','')}-001`;
  protected operatorToken='';
  protected primaryId='';
  protected backupId='';
  protected readonly selectedKoza=signal<Set<string>>(new Set());
  protected batchReference=this.newReference('KOZA', false);
  protected kozaInstrumentId='';
  protected kozaAmount=10000;
  protected kozaReference=this.newReference('KOZA-API');
  protected kozaTargetDate=this.nextKozaDate();
  protected batchAmounts:Record<string,number>={};

  protected readonly selected=computed(()=>this.instruments().find(item=>item.instrumentId===this.selectedId())??null);
  protected readonly kozaInstruments=computed(()=>this.instruments().filter(item=>
    item.method==='KOZA_FURIKAE_SELECT' && (!this.liveCallsEnabled() || item.metadata['prototype']!==true)));

  ngOnInit():void{
    forkJoin({
      experience:this.checkoutApi.getCheckoutExperience(),
      browser:this.checkoutApi.getBrowserConfiguration()
    }).subscribe({
      next:configuration=>{
        this.operatorTokenRequired.set(configuration.experience.operatorTokenRequired);
        this.liveCallsEnabled.set(configuration.browser.liveCallsEnabled);
        this.reload();
      },
      error:()=>this.reload()
    });
  }

  protected choose(id:string):void{
    this.selectedId.set(id);
    const item=this.selected();
    if(item){
      if(!this.supportsFlexibleExecution(item))this.execution='CAPTURE';
      const customer=this.instruments().filter(i=>i.customerCode===item.customerCode);
      this.primaryId=customer.find(i=>i.preferenceRole==='PRIMARY')?.instrumentId??id;
      this.backupId=customer.find(i=>i.preferenceRole==='BACKUP')?.instrumentId??'';
    }
  }

  protected supportsFlexibleExecution(item:PaymentInstrument|null=this.selected()):boolean{return item?.method==='CARD'||item?.method==='PAYPAY';}
  protected customerInstruments():PaymentInstrument[]{const item=this.selected();return item?this.instruments().filter(i=>i.customerCode===item.customerCode):[];}

  protected submit():void{
    const item=this.selected();
    if(!item){this.message.set('Select an instrument.');return;}
    if(!this.operatorReady())return;
    const mode=this.supportsFlexibleExecution(item)?this.execution:'CAPTURE';
    const label=mode==='AUTH'?'authorize now and capture later':'run an immediate sale';
    if(!window.confirm(`Submit JPY ${this.amount.toLocaleString()} using ${item.maskedDisplay} and ${label}?`))return;
    this.submitting.set(true);this.message.set(null);
    this.api.submitMit(item.instrumentId,this.amount,this.reference,mode,this.operatorToken,crypto.randomUUID()).subscribe({
      next:result=>{this.result.set(result);this.submitting.set(false);this.message.set(`${result.state} · ${result.transactionId}`);},
      error:()=>{this.submitting.set(false);this.message.set('The recurring payment could not be submitted. Check the instrument and transaction thread.');}
    });
  }

  protected savePreferences():void{
    const item=this.selected();if(!item||!this.operatorReady())return;
    this.api.setPreferences(item.customerCode,this.primaryId,this.backupId||null,this.operatorToken).subscribe({
      next:()=>{this.message.set('Primary and backup preferences saved.');this.reload();},
      error:()=>this.message.set('Preferences could not be saved.')
    });
  }

  protected toggleKoza(id:string,checked:boolean):void{
    const selected=new Set(this.selectedKoza());checked?selected.add(id):selected.delete(id);this.selectedKoza.set(selected);
    if(checked && this.batchAmounts[id]===undefined)this.batchAmounts={...this.batchAmounts,[id]:10000};
  }

  protected setBatchAmount(id:string,value:number):void{this.batchAmounts={...this.batchAmounts,[id]:Number(value)};}

  /** Submit one future debit immediately through the operator API. */
  protected submitKozaApi():void{
    const item=this.kozaInstruments().find(value=>value.instrumentId===this.kozaInstrumentId);
    if(!item){this.message.set('Select a registered Koza mandate.');return;}
    if(!this.operatorReady())return;
    if(!window.confirm(`Submit a JPY ${this.kozaAmount.toLocaleString()} Koza debit request for ${item.customerName}? The result arrives asynchronously.`))return;
    this.submitting.set(true);this.message.set(null);
    this.api.submitKozaDebit({instrumentId:item.instrumentId,amountJpy:this.kozaAmount,
      merchantReference:this.kozaReference,targetDate:this.kozaTargetDate},this.operatorToken).subscribe({
      next:result=>{
        this.submitting.set(false);this.result.set(result);
        this.message.set(result.state==='SCHEDULED'
          ? `${result.state} · ${result.transactionId}. This is scheduled, not yet paid.`
          : `${result.state} · ${result.transactionId}. GMO did not schedule this debit; inspect its transaction thread.`);
        this.kozaReference=this.newReference('KOZA-API');
      },
      error:response=>{this.submitting.set(false);this.message.set(response?.error?.detail??'The Koza debit request could not be submitted.');}
    });
  }

  /** Submit every selected mandate as its own independently traceable payment. */
  protected submitKozaBatch():void{
    if(!this.selectedKoza().size){this.message.set('Select at least one mandate.');return;}
    if(!this.operatorReady())return;
    const confirmation=window.prompt(`Type ${this.batchReference} to submit ${this.selectedKoza().size} separate debit request(s).`);
    if(confirmation!==this.batchReference){this.message.set('Batch submission cancelled.');return;}
    const items=this.kozaInstruments().filter(i=>this.selectedKoza().has(i.instrumentId))
      .map(i=>({instrumentId:i.instrumentId,amountJpy:Number(this.batchAmounts[i.instrumentId]??10000)}));
    const date=this.kozaTargetDate;
    this.submitting.set(true);
    this.api.submitKozaBatch({batchReference:this.batchReference,cycleYear:Number(date.slice(0,4)),
      cycleMonth:Number(date.slice(4,6)),targetDate:date,submissionCutoffAt:'Operator submission',
      expectedResultDate:date,items},this.operatorToken).subscribe({
      next:result=>{
        this.submitting.set(false);this.selectedKoza.set(new Set());
        const scheduled=result.payments.filter(payment=>payment.state==='SCHEDULED').length;
        this.message.set(`${scheduled} of ${result.submittedCount} requests scheduled · ${result.batchId}. Inspect failed threads; bank results for scheduled debits are still pending.`);
        this.batchReference=this.newReference('KOZA', false);
      },
      error:()=>{this.submitting.set(false);this.message.set('The Koza batch could not be submitted.');}
    });
  }

  private operatorReady():boolean{
    if(this.operatorTokenRequired()&&!this.operatorToken){this.message.set('Enter the operator token.');return false;}
    return true;
  }

  private reload():void{
    this.api.instruments().subscribe(items=>{
      this.instruments.set(items);
      const primary=items.find(i=>i.preferenceRole==='PRIMARY'&&i.method!=='KOZA_FURIKAE_SELECT')??items.find(i=>i.method!=='KOZA_FURIKAE_SELECT');
      if(primary)this.choose(primary.instrumentId);
      const firstKoza=this.kozaInstruments()[0];
      if(firstKoza&&!this.kozaInstruments().some(i=>i.instrumentId===this.kozaInstrumentId))this.kozaInstrumentId=firstKoza.instrumentId;
      this.batchAmounts=Object.fromEntries(items.filter(i=>i.method==='KOZA_FURIKAE_SELECT').map(i=>[i.instrumentId,this.batchAmounts[i.instrumentId]??10000]));
    });
  }

  private nextKozaDate():string{
    const date=new Date();
    if(date.getDate()>15)date.setMonth(date.getMonth()+1);
    date.setDate(27);
    return `${date.getFullYear()}${String(date.getMonth()+1).padStart(2,'0')}${String(date.getDate()).padStart(2,'0')}`;
  }

  /** References are human-readable but must remain unique across reloads. */
  private newReference(prefix:string, includeDay=true):string{
    const iso=new Date().toISOString();
    const date=(includeDay?iso.slice(0,10):iso.slice(0,7)).replaceAll('-','');
    const nonce=crypto.randomUUID().replaceAll('-','').slice(0,6).toUpperCase();
    return `${prefix}-${date}-${nonce}`;
  }
}
